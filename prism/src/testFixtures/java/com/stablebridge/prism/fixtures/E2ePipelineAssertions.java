package com.stablebridge.prism.fixtures;

import static com.stablebridge.prism.fixtures.E2eBlockFixture.LAMPORTS_PER_SOL;
import static com.stablebridge.prism.fixtures.E2eBlockFixture.SOME_FAILED_TX_FEE_PAYER_BASE58;
import static com.stablebridge.prism.fixtures.E2eBlockFixture.SOME_FAILED_TX_FEE_PAYER_POST_LAMPORTS;
import static com.stablebridge.prism.fixtures.E2eBlockFixture.SOME_FAILED_TX_SIGNATURE_BASE58;
import static com.stablebridge.prism.fixtures.E2eBlockFixture.SOME_LARGE_TRANSFER_AMOUNT_LAMPORTS;
import static com.stablebridge.prism.fixtures.E2eBlockFixture.SOME_LARGE_TRANSFER_FEE_PAYER_BASE58;
import static com.stablebridge.prism.fixtures.E2eBlockFixture.SOME_LARGE_TRANSFER_FEE_PAYER_POST_LAMPORTS;
import static com.stablebridge.prism.fixtures.E2eBlockFixture.SOME_LARGE_TRANSFER_SIGNATURE_BASE58;
import static com.stablebridge.prism.fixtures.E2eBlockFixture.SOME_MEMO_TEXT;
import static com.stablebridge.prism.fixtures.E2eBlockFixture.SOME_MEMO_TX_FEE_PAYER_BASE58;
import static com.stablebridge.prism.fixtures.E2eBlockFixture.SOME_MEMO_TX_FEE_PAYER_POST_LAMPORTS;
import static com.stablebridge.prism.fixtures.E2eBlockFixture.SOME_MEMO_TX_SIGNATURE_BASE58;
import static com.stablebridge.prism.fixtures.E2eBlockFixture.SOME_SLOT;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.SQLException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.stablebridge.prism.api.AccountResponse;
import com.stablebridge.prism.api.MemoResponse;
import com.stablebridge.prism.api.Page;
import com.stablebridge.prism.api.TransactionResponse;
import com.stablebridge.prism.api.TransferResponse;

public final class E2ePipelineAssertions {

    private E2ePipelineAssertions() {}

    public static void assertTransactionsListEndpoint(HttpClient httpClient, int port, ObjectMapper mapper)
            throws Exception {
        var response = httpClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/api/transactions?limit=50"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        var page = mapper.readValue(response.body(), new TypeReference<Page<TransactionResponse>>() {});
        var expected = Page.<TransactionResponse>builder()
                .data(List.of(
                        TransactionResponse.builder()
                                .signature(SOME_LARGE_TRANSFER_SIGNATURE_BASE58)
                                .slot(SOME_SLOT)
                                .success(true)
                                .build(),
                        TransactionResponse.builder()
                                .signature(SOME_MEMO_TX_SIGNATURE_BASE58)
                                .slot(SOME_SLOT)
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

    public static void assertLargeTransferBySignature(HttpClient httpClient, int port, ObjectMapper mapper)
            throws Exception {
        assertSuccessfulTransactionBySignature(httpClient, port, mapper, SOME_LARGE_TRANSFER_SIGNATURE_BASE58);
    }

    public static void assertMemoTransactionBySignature(HttpClient httpClient, int port, ObjectMapper mapper)
            throws Exception {
        assertSuccessfulTransactionBySignature(httpClient, port, mapper, SOME_MEMO_TX_SIGNATURE_BASE58);
    }

    public static void assertSuccessfulTransactionsForSlot(
            HttpClient httpClient, int port, ObjectMapper mapper) throws Exception {
        var response = httpClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/api/slots/" + SOME_SLOT))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        var list = mapper.readValue(response.body(), new TypeReference<List<TransactionResponse>>() {});
        var expected = List.of(
                TransactionResponse.builder()
                        .signature(SOME_LARGE_TRANSFER_SIGNATURE_BASE58)
                        .slot(SOME_SLOT)
                        .success(true)
                        .build(),
                TransactionResponse.builder()
                        .signature(SOME_MEMO_TX_SIGNATURE_BASE58)
                        .slot(SOME_SLOT)
                        .success(true)
                        .build());
        assertThat(list)
                .usingRecursiveComparison()
                .ignoringCollectionOrder()
                .ignoringFieldsOfTypes(Instant.class)
                .isEqualTo(expected);
    }

    public static void assertLargeTransferViaTransfersEndpoint(
            HttpClient httpClient, int port, ObjectMapper mapper) throws Exception {
        var response = httpClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/api/transfers?min_amount=0"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        var page = mapper.readValue(response.body(), new TypeReference<Page<TransferResponse>>() {});
        var expected = Page.<TransferResponse>builder()
                .data(List.of(TransferResponse.builder()
                        .signature(SOME_LARGE_TRANSFER_SIGNATURE_BASE58)
                        .slot(SOME_SLOT)
                        .amount((double) SOME_LARGE_TRANSFER_AMOUNT_LAMPORTS / (double) LAMPORTS_PER_SOL)
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

    public static void assertMemoViaMemosEndpoint(HttpClient httpClient, int port, ObjectMapper mapper)
            throws Exception {
        var response = httpClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/api/memos"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        var page = mapper.readValue(response.body(), new TypeReference<Page<MemoResponse>>() {});
        var expected = Page.<MemoResponse>builder()
                .data(List.of(MemoResponse.builder()
                        .signature(SOME_MEMO_TX_SIGNATURE_BASE58)
                        .memo(SOME_MEMO_TEXT)
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

    public static void assertLargeTransferFeePayerAccount(
            HttpClient httpClient, int port, ObjectMapper mapper) throws Exception {
        assertFeePayerAccount(
                httpClient,
                port,
                mapper,
                SOME_LARGE_TRANSFER_FEE_PAYER_BASE58,
                SOME_LARGE_TRANSFER_FEE_PAYER_POST_LAMPORTS);
    }

    public static void assertFailedFeePayerAccount(HttpClient httpClient, int port, ObjectMapper mapper)
            throws Exception {
        assertFeePayerAccount(
                httpClient,
                port,
                mapper,
                SOME_FAILED_TX_FEE_PAYER_BASE58,
                SOME_FAILED_TX_FEE_PAYER_POST_LAMPORTS);
    }

    public static void assertMemoFeePayerAccount(HttpClient httpClient, int port, ObjectMapper mapper)
            throws Exception {
        assertFeePayerAccount(
                httpClient,
                port,
                mapper,
                SOME_MEMO_TX_FEE_PAYER_BASE58,
                SOME_MEMO_TX_FEE_PAYER_POST_LAMPORTS);
    }

    public static void assertFailedTransactionRow(DataSource readPool) throws SQLException {
        var rows = new ArrayList<FailedTransactionRow>();
        try (var conn = readPool.getConnection();
                var ps = conn.prepareStatement("SELECT signature, slot FROM failed_transactions");
                var rs = ps.executeQuery()) {
            while (rs.next()) {
                rows.add(new FailedTransactionRow(rs.getString("signature"), rs.getLong("slot")));
            }
        }
        var expected = List.of(new FailedTransactionRow(SOME_FAILED_TX_SIGNATURE_BASE58, SOME_SLOT));
        assertThat(rows).usingRecursiveComparison().isEqualTo(expected);
    }

    public static long countTable(DataSource dataSource, String table) {
        try (var conn = dataSource.getConnection();
                var ps = conn.prepareStatement("SELECT COUNT(*) FROM " + table);
                var rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to count " + table, e);
        }
    }

    public static void truncateAllTables(DataSource dataSource) throws SQLException {
        try (var conn = dataSource.getConnection();
                var stmt = conn.createStatement()) {
            stmt.execute(
                    "TRUNCATE transactions, staging_transactions, failed_transactions, large_transfers, memos, accounts");
        }
    }

    private static void assertSuccessfulTransactionBySignature(
            HttpClient httpClient, int port, ObjectMapper mapper, String signatureBase58) throws Exception {
        var response = httpClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/api/transactions/" + signatureBase58))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        var body = mapper.readValue(response.body(), TransactionResponse.class);
        var expected = TransactionResponse.builder()
                .signature(signatureBase58)
                .slot(SOME_SLOT)
                .success(true)
                .build();
        assertThat(body)
                .usingRecursiveComparison()
                .ignoringFieldsOfTypes(Instant.class)
                .isEqualTo(expected);
    }

    private static void assertFeePayerAccount(
            HttpClient httpClient, int port, ObjectMapper mapper, String pubkeyBase58, long lamports)
            throws Exception {
        var response = httpClient.send(
                HttpRequest.newBuilder()
                        .uri(URI.create("http://localhost:" + port + "/api/accounts/" + pubkeyBase58))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).isEqualTo(200);
        var body = mapper.readValue(response.body(), AccountResponse.class);
        var expected = AccountResponse.builder()
                .pubkey(pubkeyBase58)
                .lamports(lamports)
                .slot(SOME_SLOT)
                .executable(false)
                .rentEpoch(0L)
                .build();
        assertThat(body)
                .usingRecursiveComparison()
                .ignoringFieldsOfTypes(Instant.class)
                .isEqualTo(expected);
    }

    public record FailedTransactionRow(String signature, long slot) {}
}
