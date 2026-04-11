package com.stablebridge.prism.e2e;

import static com.stablebridge.prism.fixtures.IndexerConfigFixtures.indexerConfigBuilder;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.net.http.HttpClient;
import java.util.UUID;
import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.stablebridge.prism.application.IndexerLifecycle;
import com.stablebridge.prism.application.TestIndexerApplication;
import com.stablebridge.prism.fixtures.E2eBlockFixture;
import com.stablebridge.prism.fixtures.E2ePipelineAssertions;
import com.stablebridge.prism.infrastructure.grpc.ReconnectHandler;
import com.stablebridge.prism.infrastructure.grpc.TransactionParser;
import com.stablebridge.prism.infrastructure.grpc.YellowstoneTransactionStream;
import com.stablebridge.prism.infrastructure.grpc.proto.geyser.GeyserGrpc;
import com.stablebridge.prism.infrastructure.grpc.proto.geyser.SubscribeRequest;
import com.stablebridge.prism.infrastructure.grpc.proto.geyser.SubscribeUpdate;
import com.stablebridge.prism.testutil.SharedPostgresContainer;

import io.grpc.ManagedChannel;
import io.grpc.Server;
import io.grpc.inprocess.InProcessChannelBuilder;
import io.grpc.inprocess.InProcessServerBuilder;
import io.grpc.stub.StreamObserver;

class GrpcEndToEndIntegrationTest {

    static DataSource readPool;
    static Server grpcServer;
    static ManagedChannel grpcChannel;
    static TestGeyserService geyserService;
    static IndexerLifecycle lifecycle;
    static HttpClient httpClient;
    static ObjectMapper objectMapper;

    @BeforeAll
    static void startPipelineAndDispatchBlock() throws Exception {
        var writePool = SharedPostgresContainer.writePool();
        readPool = SharedPostgresContainer.readPool();
        E2ePipelineAssertions.truncateAllTables(writePool);

        var serverName = "grpc-e2e-" + UUID.randomUUID();
        geyserService = new TestGeyserService();
        grpcServer = InProcessServerBuilder.forName(serverName)
                .directExecutor()
                .addService(geyserService)
                .build()
                .start();
        grpcChannel = InProcessChannelBuilder.forName(serverName)
                .directExecutor()
                .build();

        var stream = new YellowstoneTransactionStream(
                grpcChannel, new TransactionParser(), new ReconnectHandler(), null);
        var config = indexerConfigBuilder()
                .databaseUrl("jdbc:postgresql://unused")
                .benchLog("build/grpc-e2e-benchmark.log")
                .consoleLog(false)
                .apiPort(0)
                .build();
        lifecycle = TestIndexerApplication.start(config, writePool, readPool, stream);

        httpClient = HttpClient.newHttpClient();
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        await().atMost(10, SECONDS).until(() -> geyserService.responseObserver() != null);
        E2eBlockFixture.grpcUpdates()
                .forEach(update ->
                        geyserService.send(SubscribeUpdate.newBuilder().setTransaction(update).build()));

        await().atMost(15, SECONDS)
                .untilAsserted(() ->
                        assertThat(E2ePipelineAssertions.countTable(readPool, "transactions")).isEqualTo(2L));
        await().atMost(15, SECONDS)
                .untilAsserted(() -> assertThat(E2ePipelineAssertions.countTable(readPool, "failed_transactions"))
                        .isEqualTo(1L));
        await().atMost(15, SECONDS)
                .untilAsserted(() ->
                        assertThat(E2ePipelineAssertions.countTable(readPool, "memos")).isEqualTo(1L));
        await().atMost(15, SECONDS)
                .untilAsserted(() -> assertThat(E2ePipelineAssertions.countTable(readPool, "large_transfers"))
                        .isEqualTo(1L));
        await().atMost(15, SECONDS)
                .untilAsserted(() ->
                        assertThat(E2ePipelineAssertions.countTable(readPool, "accounts")).isEqualTo(3L));
    }

    @AfterAll
    static void stopPipeline() {
        if (lifecycle != null) {
            lifecycle.stop();
        }
        if (grpcChannel != null) {
            grpcChannel.shutdownNow();
        }
        if (grpcServer != null) {
            grpcServer.shutdownNow();
        }
    }

    @Test
    void shouldExposeSuccessfulTransactionsViaListEndpoint() throws Exception {
        // given / when / then
        E2ePipelineAssertions.assertTransactionsListEndpoint(httpClient, lifecycle.port(), objectMapper);
    }

    @Test
    void shouldReturnLargeTransferTransactionBySignature() throws Exception {
        // given / when / then
        E2ePipelineAssertions.assertLargeTransferBySignature(httpClient, lifecycle.port(), objectMapper);
    }

    @Test
    void shouldReturnMemoTransactionBySignature() throws Exception {
        // given / when / then
        E2ePipelineAssertions.assertMemoTransactionBySignature(httpClient, lifecycle.port(), objectMapper);
    }

    @Test
    void shouldReturnBothSuccessfulTransactionsForSlot() throws Exception {
        // given / when / then
        E2ePipelineAssertions.assertSuccessfulTransactionsForSlot(httpClient, lifecycle.port(), objectMapper);
    }

    @Test
    void shouldReturnLargeTransferViaTransfersEndpoint() throws Exception {
        // given / when / then
        E2ePipelineAssertions.assertLargeTransferViaTransfersEndpoint(
                httpClient, lifecycle.port(), objectMapper);
    }

    @Test
    void shouldReturnMemoViaMemosEndpoint() throws Exception {
        // given / when / then
        E2ePipelineAssertions.assertMemoViaMemosEndpoint(httpClient, lifecycle.port(), objectMapper);
    }

    @Test
    void shouldReturnLargeTransferFeePayerAccount() throws Exception {
        // given / when / then
        E2ePipelineAssertions.assertLargeTransferFeePayerAccount(httpClient, lifecycle.port(), objectMapper);
    }

    @Test
    void shouldReturnFailedFeePayerAccount() throws Exception {
        // given / when / then
        E2ePipelineAssertions.assertFailedFeePayerAccount(httpClient, lifecycle.port(), objectMapper);
    }

    @Test
    void shouldReturnMemoFeePayerAccount() throws Exception {
        // given / when / then
        E2ePipelineAssertions.assertMemoFeePayerAccount(httpClient, lifecycle.port(), objectMapper);
    }

    @Test
    void shouldPersistFailedTransactionRow() throws Exception {
        // given / when / then
        E2ePipelineAssertions.assertFailedTransactionRow(readPool);
    }

    private static final class TestGeyserService extends GeyserGrpc.GeyserImplBase {

        private volatile StreamObserver<SubscribeUpdate> responseObserver;

        @Override
        public StreamObserver<SubscribeRequest> subscribe(StreamObserver<SubscribeUpdate> observer) {
            this.responseObserver = observer;
            return new StreamObserver<>() {
                @Override
                public void onNext(SubscribeRequest value) {}

                @Override
                public void onError(Throwable t) {
                    responseObserver = null;
                }

                @Override
                public void onCompleted() {
                    responseObserver = null;
                }
            };
        }

        StreamObserver<SubscribeUpdate> responseObserver() {
            return responseObserver;
        }

        void send(SubscribeUpdate update) {
            var observer = responseObserver;
            if (observer != null) {
                observer.onNext(update);
            }
        }
    }
}
