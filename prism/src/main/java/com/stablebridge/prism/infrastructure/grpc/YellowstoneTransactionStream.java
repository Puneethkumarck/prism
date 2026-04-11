package com.stablebridge.prism.infrastructure.grpc;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import java.util.function.Supplier;

import com.stablebridge.prism.domain.model.Account;
import com.stablebridge.prism.domain.model.SolanaTransaction;
import com.stablebridge.prism.domain.port.TransactionStream;
import com.stablebridge.prism.infrastructure.grpc.proto.geyser.CommitmentLevel;
import com.stablebridge.prism.infrastructure.grpc.proto.geyser.GeyserGrpc;
import com.stablebridge.prism.infrastructure.grpc.proto.geyser.SubscribeRequest;
import com.stablebridge.prism.infrastructure.grpc.proto.geyser.SubscribeRequestFilterSlots;
import com.stablebridge.prism.infrastructure.grpc.proto.geyser.SubscribeRequestFilterTransactions;
import com.stablebridge.prism.infrastructure.grpc.proto.geyser.SubscribeRequestPing;
import com.stablebridge.prism.infrastructure.grpc.proto.geyser.SubscribeUpdate;

import io.grpc.Channel;
import io.grpc.Metadata;
import io.grpc.stub.MetadataUtils;
import io.grpc.stub.StreamObserver;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class YellowstoneTransactionStream implements TransactionStream {

    static final String TRANSACTIONS_FILTER_KEY = "all";
    static final String SLOTS_FILTER_KEY = "slots";
    static final Metadata.Key<String> X_TOKEN_HEADER =
            Metadata.Key.of("x-token", Metadata.ASCII_STRING_MARSHALLER);

    private final Channel channel;
    private final TransactionParser parser;
    private final ReconnectHandler reconnectHandler;
    private final String xToken;
    private final Supplier<Instant> clock;

    private final AtomicBoolean running = new AtomicBoolean(false);
    private final AtomicReference<StreamObserver<SubscribeRequest>> requestObserver = new AtomicReference<>();
    private final AtomicReference<Thread> loopThread = new AtomicReference<>();

    public YellowstoneTransactionStream(
            Channel channel, TransactionParser parser, ReconnectHandler reconnectHandler, String xToken) {
        this(channel, parser, reconnectHandler, xToken, Instant::now);
    }

    YellowstoneTransactionStream(
            Channel channel,
            TransactionParser parser,
            ReconnectHandler reconnectHandler,
            String xToken,
            Supplier<Instant> clock) {
        this.channel = channel;
        this.parser = parser;
        this.reconnectHandler = reconnectHandler;
        this.xToken = xToken;
        this.clock = clock;
    }

    @Override
    public void subscribe(Consumer<SolanaTransaction> txConsumer, Consumer<Account> acctConsumer) {
        if (!running.compareAndSet(false, true)) {
            return;
        }
        var thread = Thread.ofVirtual()
                .name("yellowstone-tx-stream")
                .start(() -> runLoop(txConsumer, acctConsumer));
        loopThread.set(thread);
    }

    @Override
    public void close() {
        if (!running.compareAndSet(true, false)) {
            return;
        }
        var observer = requestObserver.getAndSet(null);
        if (observer != null) {
            try {
                observer.onCompleted();
            } catch (RuntimeException e) {
                log.debug("ignored exception while closing request stream", e);
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
                openStream(txConsumer, acctConsumer, latch, connectedAt);
                latch.await();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            } catch (RuntimeException e) {
                log.warn("yellowstone subscribe attempt failed", e);
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

    private void openStream(
            Consumer<SolanaTransaction> txConsumer,
            Consumer<Account> acctConsumer,
            CountDownLatch latch,
            Instant connectedAt) {
        var stub = newStub();
        var responseObserver = new SubscribeResponseObserver(txConsumer, acctConsumer, latch, connectedAt);
        var requests = stub.subscribe(responseObserver);
        requestObserver.set(requests);
        responseObserver.bindRequestObserver(requests);
        requests.onNext(buildInitialRequest());
    }

    private GeyserGrpc.GeyserStub newStub() {
        var stub = GeyserGrpc.newStub(channel);
        if (xToken == null || xToken.isBlank()) {
            return stub;
        }
        var headers = new Metadata();
        headers.put(X_TOKEN_HEADER, xToken);
        return stub.withInterceptors(MetadataUtils.newAttachHeadersInterceptor(headers));
    }

    static SubscribeRequest buildInitialRequest() {
        var txFilter = SubscribeRequestFilterTransactions.newBuilder()
                .setVote(false)
                .build();
        var slotFilter = SubscribeRequestFilterSlots.newBuilder().build();
        return SubscribeRequest.newBuilder()
                .putTransactions(TRANSACTIONS_FILTER_KEY, txFilter)
                .putSlots(SLOTS_FILTER_KEY, slotFilter)
                .setCommitment(CommitmentLevel.CONFIRMED)
                .build();
    }

    private void recordConnectedDuration(Instant connectedAt) {
        var connectedFor = Duration.between(connectedAt, clock.get()).toSeconds();
        reconnectHandler.resetIfStable(connectedFor);
    }

    private final class SubscribeResponseObserver implements StreamObserver<SubscribeUpdate> {

        private final Consumer<SolanaTransaction> txConsumer;
        private final Consumer<Account> acctConsumer;
        private final CountDownLatch latch;
        private final Instant connectedAt;
        private final AtomicReference<StreamObserver<SubscribeRequest>> requests = new AtomicReference<>();

        private SubscribeResponseObserver(
                Consumer<SolanaTransaction> txConsumer,
                Consumer<Account> acctConsumer,
                CountDownLatch latch,
                Instant connectedAt) {
            this.txConsumer = txConsumer;
            this.acctConsumer = acctConsumer;
            this.latch = latch;
            this.connectedAt = connectedAt;
        }

        private void bindRequestObserver(StreamObserver<SubscribeRequest> observer) {
            requests.set(observer);
        }

        @Override
        public void onNext(SubscribeUpdate update) {
            switch (update.getUpdateOneofCase()) {
                case TRANSACTION -> handleTransaction(update);
                case SLOT -> log.info("[SLOT] {}", update.getSlot().getSlot());
                case PING -> respondToPing();
                default -> {}
            }
        }

        @Override
        public void onError(Throwable t) {
            log.warn("yellowstone subscribe stream errored", t);
            recordConnectedDuration(connectedAt);
            latch.countDown();
        }

        @Override
        public void onCompleted() {
            log.info("yellowstone subscribe stream completed");
            recordConnectedDuration(connectedAt);
            latch.countDown();
        }

        private void handleTransaction(SubscribeUpdate update) {
            var txUpdate = update.getTransaction();
            parser.parseTransaction(txUpdate).ifPresent(txConsumer);
            parser.extractFeePayer(txUpdate).ifPresent(acctConsumer);
        }

        private void respondToPing() {
            var observer = requests.get();
            if (observer == null) {
                return;
            }
            var pong = SubscribeRequest.newBuilder()
                    .setPing(SubscribeRequestPing.newBuilder().setId(1).build())
                    .build();
            try {
                observer.onNext(pong);
            } catch (RuntimeException e) {
                log.debug("ignored exception while responding to ping", e);
            }
        }
    }
}
