package com.stablebridge.prism.infrastructure.websocket;

import static com.stablebridge.prism.fixtures.IndexerConfigFixtures.indexerConfigBuilder;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.net.http.HttpClient;
import java.net.http.WebSocket;
import java.time.Instant;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
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
import com.stablebridge.prism.testutil.SharedPostgresContainer;

class WebSocketEndToEndIntegrationTest {

    private static final String SOME_WS_ENDPOINT = "wss://prism-e2e.test/blocks";
    private static final String BLOCK_SUBSCRIBE_PAYLOAD = WebSocketTransactionStream.BLOCK_SUBSCRIBE_PAYLOAD;

    static DataSource readPool;
    static IndexerLifecycle lifecycle;
    static HttpClient httpClient;
    static ObjectMapper objectMapper;

    @BeforeAll
    static void startPipelineAndDispatchBlock() throws Exception {
        var writePool = SharedPostgresContainer.writePool();
        readPool = SharedPostgresContainer.readPool();
        E2ePipelineAssertions.truncateAllTables(writePool);

        var stubSocket = mock(WebSocket.class);
        given(stubSocket.sendText(BLOCK_SUBSCRIBE_PAYLOAD, true))
                .willReturn(CompletableFuture.completedFuture(stubSocket));
        var capturedListeners = new CopyOnWriteArrayList<WebSocket.Listener>();
        WebSocketFactory factory = (uri, listener) -> {
            capturedListeners.add(listener);
            return CompletableFuture.completedFuture(stubSocket);
        };
        var stream = new WebSocketTransactionStream(
                SOME_WS_ENDPOINT,
                new BlockNotificationParser(),
                new ReconnectHandler(),
                factory,
                new ObjectMapper(),
                Instant::now);

        var config = indexerConfigBuilder()
                .databaseUrl("jdbc:postgresql://unused")
                .benchLog("build/websocket-e2e-benchmark.log")
                .consoleLog(false)
                .apiPort(0)
                .build();
        lifecycle = TestIndexerApplication.start(config, writePool, readPool, stream);

        httpClient = HttpClient.newHttpClient();
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

        await().atMost(10, SECONDS).until(() -> !capturedListeners.isEmpty());
        var listener = capturedListeners.get(0);
        listener.onText(stubSocket, E2eBlockFixture.webSocketFrame(), true);

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
}
