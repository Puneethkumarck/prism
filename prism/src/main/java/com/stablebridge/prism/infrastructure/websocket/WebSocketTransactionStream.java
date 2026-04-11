package com.stablebridge.prism.infrastructure.websocket;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stablebridge.prism.domain.model.Account;
import com.stablebridge.prism.domain.model.SolanaTransaction;
import com.stablebridge.prism.domain.port.TransactionStream;
import com.stablebridge.prism.infrastructure.grpc.ReconnectHandler;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class WebSocketTransactionStream implements TransactionStream {

    static final String BLOCK_SUBSCRIBE_PAYLOAD =
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"blockSubscribe\",\"params\":"
                    + "[\"all\",{\"commitment\":\"confirmed\",\"encoding\":\"jsonParsed\","
                    + "\"transactionDetails\":\"full\",\"maxSupportedTransactionVersion\":0}]}";

    static final String VOTE_PROGRAM_ID = "Vote111111111111111111111111111111111111111";

    private final URI endpoint;
    private final BlockNotificationParser parser;
    private final ReconnectHandler reconnectHandler;
    private final WebSocketFactory webSocketFactory;
    private final ObjectMapper objectMapper;
    private final Supplier<Instant> clock;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicReference<WebSocket> currentSocket = new AtomicReference<>();
    private final AtomicReference<Thread> loopThread = new AtomicReference<>();

    public WebSocketTransactionStream(
            String endpoint, BlockNotificationParser parser, ReconnectHandler reconnectHandler) {
        this(endpoint, parser, reconnectHandler, defaultFactory(), new ObjectMapper(), Instant::now);
    }

    WebSocketTransactionStream(
            String endpoint,
            BlockNotificationParser parser,
            ReconnectHandler reconnectHandler,
            WebSocketFactory webSocketFactory,
            ObjectMapper objectMapper,
            Supplier<Instant> clock) {
        this.endpoint = URI.create(Objects.requireNonNull(endpoint, "endpoint must not be null"));
        this.parser = Objects.requireNonNull(parser, "parser must not be null");
        this.reconnectHandler = Objects.requireNonNull(reconnectHandler, "reconnectHandler must not be null");
        this.webSocketFactory = Objects.requireNonNull(webSocketFactory, "webSocketFactory must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
    }

    @Override
    public void subscribe(Consumer<SolanaTransaction> txConsumer, Consumer<Account> acctConsumer) {
        Objects.requireNonNull(txConsumer, "txConsumer must not be null");
        Objects.requireNonNull(acctConsumer, "acctConsumer must not be null");
        if (!running.compareAndSet(false, true)) {
            return;
        }
        var thread = Thread.ofVirtual()
                .name("websocket-tx-stream")
                .unstarted(() -> runLoop(txConsumer, acctConsumer));
        loopThread.set(thread);
        thread.start();
    }

    @Override
    public void close() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        var socket = currentSocket.getAndSet(null);
        if (socket != null) {
            try {
                socket.sendClose(WebSocket.NORMAL_CLOSURE, "closed");
            } catch (RuntimeException e) {
                log.debug("ignored exception while closing websocket", e);
            }
        }
        var thread = loopThread.getAndSet(null);
        if (thread != null) {
            thread.interrupt();
        }
    }

    private void runLoop(Consumer<SolanaTransaction> txConsumer, Consumer<Account> acctConsumer) {
        while (running.get()) {
            var latch = new CountDownLatch(1);
            var connectedAt = clock.get();
            try {
                openConnection(txConsumer, acctConsumer, latch, connectedAt);
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException e) {
                log.warn("websocket subscribe attempt failed", e);
                recordConnectedDuration(connectedAt);
            }
            if (!running.get()) {
                return;
            }
            try {
                var delay = reconnectHandler.nextDelay();
                log.info("reconnecting in {}s", delay);
                TimeUnit.SECONDS.sleep(delay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
        }
    }

    private void openConnection(
            Consumer<SolanaTransaction> txConsumer,
            Consumer<Account> acctConsumer,
            CountDownLatch latch,
            Instant connectedAt) {
        var listener = new BlockSubscribeListener(txConsumer, acctConsumer, latch, connectedAt);
        var socket = webSocketFactory.connect(endpoint, listener).join();
        if (!running.get() || Thread.currentThread() != loopThread.get()) {
            try {
                socket.sendClose(WebSocket.NORMAL_CLOSURE, "closed");
            } catch (RuntimeException e) {
                log.debug("ignored exception while closing stale websocket", e);
            }
            return;
        }
        currentSocket.set(socket);
        socket.sendText(BLOCK_SUBSCRIBE_PAYLOAD, true);
    }

    private void recordConnectedDuration(Instant connectedAt) {
        var connectedFor = Duration.between(connectedAt, clock.get()).toSeconds();
        reconnectHandler.resetIfStable(connectedFor);
    }

    private static WebSocketFactory defaultFactory() {
        var client = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        return (uri, listener) -> client.newWebSocketBuilder().buildAsync(uri, listener);
    }

    private final class BlockSubscribeListener implements WebSocket.Listener {

        private final Consumer<SolanaTransaction> txConsumer;
        private final Consumer<Account> acctConsumer;
        private final CountDownLatch latch;
        private final Instant connectedAt;
        private final StringBuilder buffer = new StringBuilder();

        private BlockSubscribeListener(
                Consumer<SolanaTransaction> txConsumer,
                Consumer<Account> acctConsumer,
                CountDownLatch latch,
                Instant connectedAt) {
            this.txConsumer = txConsumer;
            this.acctConsumer = acctConsumer;
            this.latch = latch;
            this.connectedAt = connectedAt;
        }

        @Override
        public void onOpen(WebSocket webSocket) {
            webSocket.request(Long.MAX_VALUE);
        }

        @Override
        public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
            buffer.append(data);
            if (last) {
                var frame = buffer.toString();
                buffer.setLength(0);
                handleFrame(frame);
            }
            return null;
        }

        @Override
        public CompletionStage<?> onClose(WebSocket webSocket, int statusCode, String reason) {
            log.info("websocket closed: {} {}", statusCode, reason);
            recordConnectedDuration(connectedAt);
            latch.countDown();
            return null;
        }

        @Override
        public void onError(WebSocket webSocket, Throwable error) {
            log.warn("websocket errored", error);
            recordConnectedDuration(connectedAt);
            latch.countDown();
        }

        private void handleFrame(String frame) {
            JsonNode root;
            try {
                root = objectMapper.readTree(frame);
            } catch (RuntimeException | java.io.IOException e) {
                log.warn("failed to parse websocket frame", e);
                return;
            }
            var value = root.path("params").path("result").path("value");
            if (!value.isObject()) {
                return;
            }
            var slot = value.path("slot").asLong();
            log.info("[SLOT {}]", slot);
            var filtered = buildFilteredBlock(slot, value.path("transactions"));

            List<SolanaTransaction> transactions = List.of();
            try {
                transactions = parser.parseBlock(filtered);
            } catch (RuntimeException e) {
                log.warn("failed to parse block transactions at slot {}", slot, e);
            }
            List<Account> feePayers = List.of();
            try {
                feePayers = parser.extractFeePayers(filtered);
            } catch (RuntimeException e) {
                log.warn("failed to extract fee payers at slot {}", slot, e);
            }

            for (var tx : transactions) {
                try {
                    txConsumer.accept(tx);
                } catch (RuntimeException e) {
                    log.warn("failed to forward transaction at slot {}", slot, e);
                }
            }
            for (var payer : feePayers) {
                try {
                    acctConsumer.accept(payer);
                } catch (RuntimeException e) {
                    log.warn("failed to forward fee payer at slot {}", slot, e);
                }
            }
        }

        private JsonNode buildFilteredBlock(long slot, JsonNode transactionsNode) {
            var result = objectMapper.createObjectNode();
            result.put("slot", slot);
            var kept = objectMapper.createArrayNode();
            if (transactionsNode.isArray()) {
                for (var tx : transactionsNode) {
                    if (!isVoteTransaction(tx)) {
                        kept.add(tx);
                    }
                }
            }
            result.set("transactions", kept);
            return result;
        }

        private boolean isVoteTransaction(JsonNode txNode) {
            var message = txNode.path("transaction").path("message");
            var instructions = message.path("instructions");
            if (!instructions.isArray()) {
                return false;
            }
            var accountKeys = extractAccountKeys(message);
            for (var instruction : instructions) {
                var programId = instruction.path("programId");
                if (programId.isTextual() && VOTE_PROGRAM_ID.equals(programId.asText())) {
                    return true;
                }
                var programIdIndex = instruction.path("programIdIndex");
                if (programIdIndex.isNumber()) {
                    var idx = programIdIndex.asInt();
                    if (idx >= 0 && idx < accountKeys.size() && VOTE_PROGRAM_ID.equals(accountKeys.get(idx))) {
                        return true;
                    }
                }
            }
            return false;
        }

        private List<String> extractAccountKeys(JsonNode message) {
            var node = message.path("accountKeys");
            if (!node.isArray()) {
                return List.of();
            }
            var keys = new ArrayList<String>(node.size());
            for (var entry : node) {
                if (entry.isTextual()) {
                    keys.add(entry.asText());
                } else if (entry.isObject() && entry.hasNonNull("pubkey")) {
                    keys.add(entry.get("pubkey").asText());
                }
            }
            return List.copyOf(keys);
        }
    }
}
