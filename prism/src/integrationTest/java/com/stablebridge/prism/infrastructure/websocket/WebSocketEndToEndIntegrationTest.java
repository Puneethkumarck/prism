package com.stablebridge.prism.infrastructure.websocket;

import static com.stablebridge.prism.fixtures.E2eBlockFixture.FAILED_TX_FEE_PAYER_BASE58;
import static com.stablebridge.prism.fixtures.E2eBlockFixture.FAILED_TX_FEE_PAYER_POST_LAMPORTS;
import static com.stablebridge.prism.fixtures.E2eBlockFixture.LARGE_TRANSFER_AMOUNT_LAMPORTS;
import static com.stablebridge.prism.fixtures.E2eBlockFixture.LARGE_TRANSFER_FEE_PAYER_BASE58;
import static com.stablebridge.prism.fixtures.E2eBlockFixture.LARGE_TRANSFER_FEE_PAYER_POST_LAMPORTS;
import static com.stablebridge.prism.fixtures.E2eBlockFixture.LARGE_TRANSFER_SIGNATURE_BASE58;
import static com.stablebridge.prism.fixtures.E2eBlockFixture.MEMO_TEXT;
import static com.stablebridge.prism.fixtures.E2eBlockFixture.MEMO_TX_FEE_PAYER_BASE58;
import static com.stablebridge.prism.fixtures.E2eBlockFixture.MEMO_TX_FEE_PAYER_POST_LAMPORTS;
import static com.stablebridge.prism.fixtures.E2eBlockFixture.MEMO_TX_SIGNATURE_BASE58;
import static com.stablebridge.prism.fixtures.E2eBlockFixture.SLOT;
import static com.stablebridge.prism.fixtures.IndexerConfigFixtures.indexerConfigBuilder;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.parallel.ResourceLock;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.stablebridge.prism.api.AccountResponse;
import com.stablebridge.prism.api.MemoResponse;
import com.stablebridge.prism.api.Page;
import com.stablebridge.prism.api.TransactionResponse;
import com.stablebridge.prism.api.TransferResponse;
import com.stablebridge.prism.application.IndexerLifecycle;
import com.stablebridge.prism.application.TestIndexerApplication;
import com.stablebridge.prism.fixtures.E2eBlockFixture;
import com.stablebridge.prism.infrastructure.grpc.ReconnectHandler;
import com.stablebridge.prism.testutil.SharedPostgresContainer;

@ResourceLock(SharedPostgresContainer.SHARED_DB_LOCK)
class WebSocketEndToEndIntegrationTest {

    private static final String SOME_WS_ENDPOINT = "wss://prism-e2e.test/blocks";
    private static final String BLOCK_SUBSCRIBE_PAYLOAD = WebSocketTransactionStream.BLOCK_SUBSCRIBE_PAYLOAD;

    static DataSource writePool;
    static DataSource readPool;
    static WebSocket stubSocket;
    static CopyOnWriteArrayList<WebSocket.Listener> capturedListeners;
    static WebSocketTransactionStream stream;
    static IndexerLifecycle lifecycle;
    static HttpClient httpClient;
    static ObjectMapper objectMapper;

    @BeforeAll
    static void startPipelineAndDispatchBlock() throws Exception {
        writePool = SharedPostgresContainer.writePool();
        readPool = SharedPostgresContainer.readPool();
        truncateTables(writePool);

        stubSocket = mock(WebSocket.class);
        given(stubSocket.sendText(BLOCK_SUBSCRIBE_PAYLOAD, true))
                .willReturn(CompletableFuture.completedFuture(stubSocket));
        capturedListeners = new CopyOnWriteArrayList<>();
        WebSocketFactory factory = (uri, listener) -> {
            capturedListeners.add(listener);
            return CompletableFuture.completedFuture(stubSocket);
        };
        stream = new WebSocketTransactionStream(
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
                .untilAsserted(() -> assertThat(countTable(readPool, "transactions")).isEqualTo(2L));
        await().atMost(15, SECONDS)
                .untilAsserted(() -> assertThat(countTable(readPool, "failed_transactions")).isEqualTo(1L));
        await().atMost(15, SECONDS)
                .untilAsserted(() -> assertThat(countTable(readPool, "memos")).isEqualTo(1L));
        await().atMost(15, SECONDS)
                .untilAsserted(() -> assertThat(countTable(readPool, "large_transfers")).isEqualTo(1L));
        await().atMost(15, SECONDS)
                .untilAsserted(() -> assertThat(countTable(readPool, "accounts")).isEqualTo(3L));
    }

    @AfterAll
    static void stopPipeline() {
        if (lifecycle != null) {
            lifecycle.stop();
        }
    }

    @Test
    void shouldExposeSuccessfulTransactionsViaListEndpoint() throws Exception {
        // given
        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + lifecycle.port() + "/api/transactions?limit=50"))
                .GET()
                .build();

        // when
        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // then
        assertThat(response.statusCode()).isEqualTo(200);
        var page = objectMapper.readValue(response.body(), new TypeReference<Page<TransactionResponse>>() {});
        var expected = Page.<TransactionResponse>builder()
                .data(List.of(
                        TransactionResponse.builder()
                                .signature(LARGE_TRANSFER_SIGNATURE_BASE58)
                                .slot(SLOT)
                                .success(true)
                                .build(),
                        TransactionResponse.builder()
                                .signature(MEMO_TX_SIGNATURE_BASE58)
                                .slot(SLOT)
                                .success(true)
                                .build()))
                .total(2L)
                .limit(50L)
                .offset(0L)
                .build();
        assertThat(page)
                .usingRecursiveComparison()
                .ignoringCollectionOrder()
                .ignoringFieldsOfTypes(Instant.class)
                .isEqualTo(expected);
    }

    @Test
    void shouldReturnLargeTransferTransactionBySignature() throws Exception {
        // given
        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + lifecycle.port()
                        + "/api/transactions/" + LARGE_TRANSFER_SIGNATURE_BASE58))
                .GET()
                .build();

        // when
        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // then
        assertThat(response.statusCode()).isEqualTo(200);
        var body = objectMapper.readValue(response.body(), TransactionResponse.class);
        var expected = TransactionResponse.builder()
                .signature(LARGE_TRANSFER_SIGNATURE_BASE58)
                .slot(SLOT)
                .success(true)
                .build();
        assertThat(body)
                .usingRecursiveComparison()
                .ignoringFieldsOfTypes(Instant.class)
                .isEqualTo(expected);
    }

    @Test
    void shouldReturnMemoTransactionBySignature() throws Exception {
        // given
        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + lifecycle.port()
                        + "/api/transactions/" + MEMO_TX_SIGNATURE_BASE58))
                .GET()
                .build();

        // when
        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // then
        assertThat(response.statusCode()).isEqualTo(200);
        var body = objectMapper.readValue(response.body(), TransactionResponse.class);
        var expected = TransactionResponse.builder()
                .signature(MEMO_TX_SIGNATURE_BASE58)
                .slot(SLOT)
                .success(true)
                .build();
        assertThat(body)
                .usingRecursiveComparison()
                .ignoringFieldsOfTypes(Instant.class)
                .isEqualTo(expected);
    }

    @Test
    void shouldReturnBothSuccessfulTransactionsForSlot() throws Exception {
        // given
        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + lifecycle.port() + "/api/slots/" + SLOT))
                .GET()
                .build();

        // when
        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // then
        assertThat(response.statusCode()).isEqualTo(200);
        var list = objectMapper.readValue(response.body(), new TypeReference<List<TransactionResponse>>() {});
        var expected = List.of(
                TransactionResponse.builder()
                        .signature(LARGE_TRANSFER_SIGNATURE_BASE58)
                        .slot(SLOT)
                        .success(true)
                        .build(),
                TransactionResponse.builder()
                        .signature(MEMO_TX_SIGNATURE_BASE58)
                        .slot(SLOT)
                        .success(true)
                        .build());
        assertThat(list)
                .usingRecursiveComparison()
                .ignoringCollectionOrder()
                .ignoringFieldsOfTypes(Instant.class)
                .isEqualTo(expected);
    }

    @Test
    void shouldReturnLargeTransferViaTransfersEndpoint() throws Exception {
        // given
        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + lifecycle.port() + "/api/transfers?min_amount=0"))
                .GET()
                .build();

        // when
        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // then
        assertThat(response.statusCode()).isEqualTo(200);
        var page = objectMapper.readValue(response.body(), new TypeReference<Page<TransferResponse>>() {});
        var expected = Page.<TransferResponse>builder()
                .data(List.of(TransferResponse.builder()
                        .signature(LARGE_TRANSFER_SIGNATURE_BASE58)
                        .slot(SLOT)
                        .amount((double) LARGE_TRANSFER_AMOUNT_LAMPORTS / 1_000_000_000d)
                        .build()))
                .total(1L)
                .limit(50L)
                .offset(0L)
                .build();
        assertThat(page)
                .usingRecursiveComparison()
                .ignoringFieldsMatchingRegexes(".*\\.id")
                .ignoringFieldsOfTypes(Instant.class)
                .isEqualTo(expected);
    }

    @Test
    void shouldReturnMemoViaMemosEndpoint() throws Exception {
        // given
        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + lifecycle.port() + "/api/memos"))
                .GET()
                .build();

        // when
        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // then
        assertThat(response.statusCode()).isEqualTo(200);
        var page = objectMapper.readValue(response.body(), new TypeReference<Page<MemoResponse>>() {});
        var expected = Page.<MemoResponse>builder()
                .data(List.of(MemoResponse.builder()
                        .signature(MEMO_TX_SIGNATURE_BASE58)
                        .memo(MEMO_TEXT)
                        .build()))
                .total(1L)
                .limit(50L)
                .offset(0L)
                .build();
        assertThat(page)
                .usingRecursiveComparison()
                .ignoringFieldsMatchingRegexes(".*\\.id")
                .ignoringFieldsOfTypes(Instant.class)
                .isEqualTo(expected);
    }

    @Test
    void shouldReturnLargeTransferFeePayerAccount() throws Exception {
        // given
        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + lifecycle.port()
                        + "/api/accounts/" + LARGE_TRANSFER_FEE_PAYER_BASE58))
                .GET()
                .build();

        // when
        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // then
        assertThat(response.statusCode()).isEqualTo(200);
        var body = objectMapper.readValue(response.body(), AccountResponse.class);
        var expected = AccountResponse.builder()
                .pubkey(LARGE_TRANSFER_FEE_PAYER_BASE58)
                .lamports(LARGE_TRANSFER_FEE_PAYER_POST_LAMPORTS)
                .slot(SLOT)
                .executable(false)
                .rentEpoch(0L)
                .build();
        assertThat(body)
                .usingRecursiveComparison()
                .ignoringFieldsOfTypes(Instant.class)
                .isEqualTo(expected);
    }

    @Test
    void shouldReturnFailedFeePayerAccount() throws Exception {
        // given
        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + lifecycle.port()
                        + "/api/accounts/" + FAILED_TX_FEE_PAYER_BASE58))
                .GET()
                .build();

        // when
        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // then
        assertThat(response.statusCode()).isEqualTo(200);
        var body = objectMapper.readValue(response.body(), AccountResponse.class);
        var expected = AccountResponse.builder()
                .pubkey(FAILED_TX_FEE_PAYER_BASE58)
                .lamports(FAILED_TX_FEE_PAYER_POST_LAMPORTS)
                .slot(SLOT)
                .executable(false)
                .rentEpoch(0L)
                .build();
        assertThat(body)
                .usingRecursiveComparison()
                .ignoringFieldsOfTypes(Instant.class)
                .isEqualTo(expected);
    }

    @Test
    void shouldReturnMemoFeePayerAccount() throws Exception {
        // given
        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + lifecycle.port()
                        + "/api/accounts/" + MEMO_TX_FEE_PAYER_BASE58))
                .GET()
                .build();

        // when
        var response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());

        // then
        assertThat(response.statusCode()).isEqualTo(200);
        var body = objectMapper.readValue(response.body(), AccountResponse.class);
        var expected = AccountResponse.builder()
                .pubkey(MEMO_TX_FEE_PAYER_BASE58)
                .lamports(MEMO_TX_FEE_PAYER_POST_LAMPORTS)
                .slot(SLOT)
                .executable(false)
                .rentEpoch(0L)
                .build();
        assertThat(body)
                .usingRecursiveComparison()
                .ignoringFieldsOfTypes(Instant.class)
                .isEqualTo(expected);
    }

    @Test
    void shouldPersistFailedTransactionRow() throws Exception {
        // given
        var sql = "SELECT signature, slot FROM failed_transactions";

        // when
        var rows = new ArrayList<FailedTransactionRow>();
        try (var conn = readPool.getConnection();
                var ps = conn.prepareStatement(sql);
                var rs = ps.executeQuery()) {
            while (rs.next()) {
                rows.add(new FailedTransactionRow(rs.getString("signature"), rs.getLong("slot")));
            }
        }

        // then
        var expected = List.of(new FailedTransactionRow(E2eBlockFixture.FAILED_TX_SIGNATURE_BASE58, SLOT));
        assertThat(rows).usingRecursiveComparison().isEqualTo(expected);
    }

    private record FailedTransactionRow(String signature, long slot) {}

    private static long countTable(DataSource dataSource, String table) {
        try (var conn = dataSource.getConnection();
                var ps = conn.prepareStatement("SELECT COUNT(*) FROM " + table);
                var rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to count " + table, e);
        }
    }

    private static void truncateTables(DataSource dataSource) throws Exception {
        try (var conn = dataSource.getConnection();
                var stmt = conn.createStatement()) {
            stmt.execute(
                    "TRUNCATE transactions, staging_transactions, failed_transactions, large_transfers, memos, accounts");
        }
    }
}
