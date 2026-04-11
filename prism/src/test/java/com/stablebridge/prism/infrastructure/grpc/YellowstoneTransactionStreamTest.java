package com.stablebridge.prism.infrastructure.grpc;

import static com.stablebridge.prism.fixtures.GeyserTestFixtures.SOME_FEE_PAYER_PUBKEY_BYTES;
import static com.stablebridge.prism.fixtures.GeyserTestFixtures.SOME_RECEIVER_PUBKEY_BYTES;
import static com.stablebridge.prism.fixtures.GeyserTestFixtures.SOME_SENDER_PUBKEY_BYTES;
import static com.stablebridge.prism.fixtures.GeyserTestFixtures.SOME_SIGNATURE_BYTES;
import static com.stablebridge.prism.fixtures.GeyserTestFixtures.txWithBalances;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import java.io.IOException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedDeque;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.stablebridge.prism.domain.model.Account;
import com.stablebridge.prism.domain.model.SolanaTransaction;
import com.stablebridge.prism.infrastructure.grpc.proto.geyser.CommitmentLevel;
import com.stablebridge.prism.infrastructure.grpc.proto.geyser.GeyserGrpc;
import com.stablebridge.prism.infrastructure.grpc.proto.geyser.SubscribeRequest;
import com.stablebridge.prism.infrastructure.grpc.proto.geyser.SubscribeRequestFilterTransactions;
import com.stablebridge.prism.infrastructure.grpc.proto.geyser.SubscribeRequestPing;
import com.stablebridge.prism.infrastructure.grpc.proto.geyser.SubscribeUpdate;
import com.stablebridge.prism.infrastructure.grpc.proto.geyser.SubscribeUpdatePing;
import com.stablebridge.prism.infrastructure.grpc.proto.geyser.SubscribeUpdateSlot;
import com.stablebridge.prism.infrastructure.grpc.proto.geyser.SubscribeUpdateTransaction;

import io.grpc.ManagedChannel;
import io.grpc.Metadata;
import io.grpc.Server;
import io.grpc.ServerCall;
import io.grpc.ServerCallHandler;
import io.grpc.ServerInterceptor;
import io.grpc.Status;
import io.grpc.StatusException;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;

class YellowstoneTransactionStreamTest {

    private static final long SOME_SLOT = 280_000_000L;
    private static final long FIVE_SOL_LAMPORTS = 5_000_000_000L;
    private static final String SOME_X_TOKEN = "test-token-123";
    private static final Metadata.Key<String> X_TOKEN_KEY =
            Metadata.Key.of("x-token", Metadata.ASCII_STRING_MARSHALLER);

    private String serverName;
    private Server server;
    private ManagedChannel channel;
    private TestGeyserService service;
    private CapturingServerInterceptor interceptor;
    private TransactionParser parser;
    private ReconnectHandler reconnectHandler;
    private YellowstoneTransactionStream stream;

    @BeforeEach
    void setUp() throws IOException {
        serverName = "yellowstone-test-" + UUID.randomUUID();
        service = new TestGeyserService();
        interceptor = new CapturingServerInterceptor();
        server = InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .addService(service)
                .intercept(interceptor)
                .build()
                .start();
        channel = InProcessChannelBuilder.forName(serverName).directExecutor().build();
        parser = new TransactionParser();
        reconnectHandler = spy(new ReconnectHandler());
        given(reconnectHandler.nextDelay()).willReturn(0L);
    }

    @AfterEach
    void tearDown() {
        if (stream != null) {
            stream.close();
        }
        channel.shutdownNow();
        server.shutdownNow();
    }

    @Test
    void shouldSendSubscribeRequestWithVoteFalseAndConfirmedCommitment() {
        // given
        stream = new YellowstoneTransactionStream(channel, parser, reconnectHandler, null);
        var txConsumer = new CopyOnWriteArrayList<SolanaTransaction>();
        var acctConsumer = new CopyOnWriteArrayList<Account>();
        var expected = SubscribeRequest.newBuilder()
                .putTransactions(
                        "all",
                        SubscribeRequestFilterTransactions.newBuilder()
                                .setVote(false)
                                .build())
                .setCommitment(CommitmentLevel.CONFIRMED)
                .build();

        // when
        stream.subscribe(txConsumer::add, acctConsumer::add);

        // then
        await().atMost(5, SECONDS).untilAsserted(() -> {
            var request = service.lastRequest();
            assertThat(request).isNotNull();
            assertThat(request).usingRecursiveComparison().isEqualTo(expected);
        });
    }

    @Test
    void shouldInjectXTokenHeaderWhenConfigured() {
        // given
        stream = new YellowstoneTransactionStream(channel, parser, reconnectHandler, SOME_X_TOKEN);

        // when
        stream.subscribe(tx -> {}, acct -> {});

        // then
        await().atMost(5, SECONDS).untilAsserted(() -> {
            var headers = interceptor.lastHeaders();
            assertThat(headers).isNotNull();
            assertThat(headers.get(X_TOKEN_KEY)).isEqualTo(SOME_X_TOKEN);
        });
    }

    @Test
    void shouldNotInjectXTokenHeaderWhenBlank() {
        // given
        stream = new YellowstoneTransactionStream(channel, parser, reconnectHandler, "   ");

        // when
        stream.subscribe(tx -> {}, acct -> {});

        // then
        await().atMost(5, SECONDS).untilAsserted(() -> {
            var headers = interceptor.lastHeaders();
            assertThat(headers).isNotNull();
            assertThat(headers.get(X_TOKEN_KEY)).isNull();
        });
    }

    @Test
    void shouldForwardTransactionAndAccountToConsumers() {
        // given
        stream = new YellowstoneTransactionStream(channel, parser, reconnectHandler, null);
        var txConsumer = new CopyOnWriteArrayList<SolanaTransaction>();
        var acctConsumer = new CopyOnWriteArrayList<Account>();
        var txUpdate = txWithBalances(
                SOME_SLOT,
                SOME_SIGNATURE_BYTES,
                List.of(SOME_FEE_PAYER_PUBKEY_BYTES, SOME_SENDER_PUBKEY_BYTES, SOME_RECEIVER_PUBKEY_BYTES),
                List.of(FIVE_SOL_LAMPORTS, FIVE_SOL_LAMPORTS, 0L),
                List.of(FIVE_SOL_LAMPORTS, 0L, FIVE_SOL_LAMPORTS));
        var expectedTx = parser.parseTransaction(txUpdate).orElseThrow();
        var expectedAccount = parser.extractFeePayer(txUpdate).orElseThrow();
        stream.subscribe(txConsumer::add, acctConsumer::add);
        await().atMost(5, SECONDS).until(() -> service.responseObserver() != null);

        // when
        service.send(SubscribeUpdate.newBuilder().setTransaction(txUpdate).build());

        // then
        await().atMost(5, SECONDS)
                .untilAsserted(() -> assertThat(txConsumer)
                        .singleElement()
                        .usingRecursiveComparison()
                        .isEqualTo(expectedTx));
        await().atMost(5, SECONDS)
                .untilAsserted(() -> assertThat(acctConsumer)
                        .singleElement()
                        .usingRecursiveComparison()
                        .isEqualTo(expectedAccount));
    }

    @Test
    void shouldRejectNullTxConsumer() {
        // given
        stream = new YellowstoneTransactionStream(channel, parser, reconnectHandler, null);

        // when / then
        assertThatThrownBy(() -> stream.subscribe(null, acct -> {}))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("txConsumer");
    }

    @Test
    void shouldRejectNullAcctConsumer() {
        // given
        stream = new YellowstoneTransactionStream(channel, parser, reconnectHandler, null);

        // when / then
        assertThatThrownBy(() -> stream.subscribe(tx -> {}, null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("acctConsumer");
    }

    @Test
    void shouldKeepStreamAliveWhenParserThrows() {
        // given
        var failingParser = mock(TransactionParser.class);
        var expectedAccount = parser.extractFeePayer(txWithBalances(
                        SOME_SLOT,
                        SOME_SIGNATURE_BYTES,
                        List.of(SOME_FEE_PAYER_PUBKEY_BYTES, SOME_SENDER_PUBKEY_BYTES, SOME_RECEIVER_PUBKEY_BYTES),
                        List.of(FIVE_SOL_LAMPORTS, FIVE_SOL_LAMPORTS, 0L),
                        List.of(FIVE_SOL_LAMPORTS, 0L, FIVE_SOL_LAMPORTS)))
                .orElseThrow();
        given(failingParser.parseTransaction(any())).willThrow(new RuntimeException("boom"));
        given(failingParser.extractFeePayer(any())).willReturn(Optional.of(expectedAccount));
        stream = new YellowstoneTransactionStream(channel, failingParser, reconnectHandler, null);
        var txConsumer = new CopyOnWriteArrayList<SolanaTransaction>();
        var acctConsumer = new CopyOnWriteArrayList<Account>();
        stream.subscribe(txConsumer::add, acctConsumer::add);
        await().atMost(5, SECONDS).until(() -> service.responseObserver() != null);

        // when
        service.send(SubscribeUpdate.newBuilder()
                .setTransaction(SubscribeUpdateTransaction.getDefaultInstance())
                .build());

        // then
        await().atMost(5, SECONDS)
                .untilAsserted(() -> assertThat(acctConsumer)
                        .singleElement()
                        .usingRecursiveComparison()
                        .isEqualTo(expectedAccount));
        assertThat(txConsumer).isEmpty();
        assertThat(service.subscribeCount()).isEqualTo(1);
    }

    @Test
    void shouldIgnoreSlotUpdatesAndNeverForwardThemToConsumers() {
        // given
        stream = new YellowstoneTransactionStream(channel, parser, reconnectHandler, null);
        var txConsumer = new CopyOnWriteArrayList<SolanaTransaction>();
        var acctConsumer = new CopyOnWriteArrayList<Account>();
        stream.subscribe(txConsumer::add, acctConsumer::add);
        await().atMost(5, SECONDS).until(() -> service.responseObserver() != null);

        // when
        service.send(SubscribeUpdate.newBuilder()
                .setSlot(SubscribeUpdateSlot.newBuilder().setSlot(SOME_SLOT).build())
                .build());

        // then
        await().during(1, SECONDS).atMost(2, SECONDS).untilAsserted(() -> {
            assertThat(txConsumer).isEmpty();
            assertThat(acctConsumer).isEmpty();
        });
    }

    @Test
    void shouldRespondWithPongOnServerPing() {
        // given
        stream = new YellowstoneTransactionStream(channel, parser, reconnectHandler, null);
        stream.subscribe(tx -> {}, acct -> {});
        await().atMost(5, SECONDS).until(() -> service.lastRequest() != null);
        var expected = SubscribeRequest.newBuilder()
                .setPing(SubscribeRequestPing.newBuilder().setId(1).build())
                .build();

        // when
        service.send(SubscribeUpdate.newBuilder()
                .setPing(SubscribeUpdatePing.getDefaultInstance())
                .build());

        // then
        await().atMost(5, SECONDS)
                .untilAsserted(() -> assertThat(service.lastRequest())
                        .usingRecursiveComparison()
                        .isEqualTo(expected));
    }

    @Test
    void shouldReconnectWithExponentialBackoffAfterStreamError() {
        // given
        var fixedNow = Instant.parse("2026-01-01T00:00:00Z");
        stream = new YellowstoneTransactionStream(channel, parser, reconnectHandler, null, () -> fixedNow);
        stream.subscribe(tx -> {}, acct -> {});
        await().atMost(5, SECONDS).until(() -> service.subscribeCount() >= 1);

        // when
        service.errorOpenStream(Status.UNAVAILABLE.withDescription("boom").asException());

        // then
        await().atMost(5, SECONDS).untilAsserted(() -> {
            assertThat(service.subscribeCount()).isGreaterThanOrEqualTo(2);
            then(reconnectHandler).should(atLeastOnce()).nextDelay();
            then(reconnectHandler).should(atLeastOnce()).resetIfStable(0L);
        });
    }

    @Test
    void shouldStopLoopOnClose() {
        // given
        stream = new YellowstoneTransactionStream(channel, parser, reconnectHandler, null);
        stream.subscribe(tx -> {}, acct -> {});
        await().atMost(5, SECONDS).until(() -> service.responseObserver() != null);
        var firstCount = service.subscribeCount();

        // when
        stream.close();

        // then
        await().during(1, SECONDS).atMost(2, SECONDS).untilAsserted(() ->
                assertThat(service.subscribeCount()).isEqualTo(firstCount));
    }

    private static final class TestGeyserService extends GeyserGrpc.GeyserImplBase {

        private final AtomicInteger subscribeCount = new AtomicInteger();
        private final ConcurrentLinkedDeque<SubscribeRequest> requests = new ConcurrentLinkedDeque<>();
        private final AtomicReference<StreamObserver<SubscribeUpdate>> current = new AtomicReference<>();

        @Override
        public StreamObserver<SubscribeRequest> subscribe(StreamObserver<SubscribeUpdate> responseObserver) {
            subscribeCount.incrementAndGet();
            current.set(responseObserver);
            return new StreamObserver<>() {
                @Override
                public void onNext(SubscribeRequest value) {
                    requests.add(value);
                }

                @Override
                public void onError(Throwable t) {
                    current.compareAndSet(responseObserver, null);
                }

                @Override
                public void onCompleted() {
                    current.compareAndSet(responseObserver, null);
                }
            };
        }

        SubscribeRequest lastRequest() {
            return requests.peekLast();
        }

        int subscribeCount() {
            return subscribeCount.get();
        }

        StreamObserver<SubscribeUpdate> responseObserver() {
            return current.get();
        }

        void send(SubscribeUpdate update) {
            var observer = current.get();
            if (observer != null) {
                observer.onNext(update);
            }
        }

        void errorOpenStream(StatusException error) {
            var observer = current.getAndSet(null);
            if (observer != null) {
                observer.onError(error);
            }
        }
    }

    private static final class CapturingServerInterceptor implements ServerInterceptor {

        private final AtomicReference<Metadata> lastHeaders = new AtomicReference<>();

        @Override
        public <ReqT, RespT> ServerCall.Listener<ReqT> interceptCall(
                ServerCall<ReqT, RespT> call,
                Metadata headers,
                ServerCallHandler<ReqT, RespT> next) {
            lastHeaders.set(headers);
            return next.startCall(call, headers);
        }

        Metadata lastHeaders() {
            return lastHeaders.get();
        }
    }
}
