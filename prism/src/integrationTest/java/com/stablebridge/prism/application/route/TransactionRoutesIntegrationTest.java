package com.stablebridge.prism.application.route;

import static com.stablebridge.prism.fixtures.TransactionFixtures.transactionBuilder;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.stablebridge.prism.api.Page;
import com.stablebridge.prism.api.TransactionResponse;
import com.stablebridge.prism.application.error.GlobalErrorHandler;
import com.stablebridge.prism.application.mapper.TransactionResponseMapper;
import com.stablebridge.prism.domain.model.Signature;
import com.stablebridge.prism.infrastructure.persistence.CopyTransactionRepository;
import com.stablebridge.prism.testutil.SharedPostgresContainer;

import io.helidon.webserver.WebServer;

class TransactionRoutesIntegrationTest {

    static DataSource writePool;
    static DataSource readPool;
    static WebServer server;
    static HttpClient client;
    static ObjectMapper objectMapper;
    static CopyTransactionRepository transactionRepository;

    @BeforeAll
    static void startServer() {
        writePool = SharedPostgresContainer.writePool();
        readPool = SharedPostgresContainer.readPool();
        transactionRepository = new CopyTransactionRepository(writePool, readPool);
        var mapper = Mappers.getMapper(TransactionResponseMapper.class);
        var routes = new TransactionRoutes(transactionRepository, mapper);
        server = WebServer.builder()
                .port(0)
                .routing(r -> {
                    r.register(routes);
                    GlobalErrorHandler.register(r);
                })
                .build()
                .start();
        client = HttpClient.newHttpClient();
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.stop();
        }
    }

    @BeforeEach
    void cleanTables() throws Exception {
        try (var conn = writePool.getConnection();
                var stmt = conn.createStatement()) {
            stmt.execute(
                    "TRUNCATE transactions, staging_transactions, failed_transactions, large_transfers, memos, accounts");
        }
    }

    @Test
    void shouldReturnPaginatedTransactions() throws Exception {
        // given
        var tx1 = transactionBuilder()
                .signature(new Signature("5Kx7aEwMbITTxPage00001"))
                .slot(100L)
                .build();
        var tx2 = transactionBuilder()
                .signature(new Signature("5Kx7aEwMbITTxPage00002"))
                .slot(200L)
                .build();
        transactionRepository.bulkInsert(List.of(tx1, tx2));

        // when
        var response = client.send(
                HttpRequest.newBuilder(URI.create(
                                "http://localhost:" + server.port() + "/api/transactions?limit=10"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        // then
        assertThat(response.statusCode()).isEqualTo(200);
        var page = objectMapper.readValue(
                response.body(), new TypeReference<Page<TransactionResponse>>() {});
        var expected = Page.<TransactionResponse>builder()
                .data(List.of(
                        TransactionResponse.builder()
                                .signature("5Kx7aEwMbITTxPage00001")
                                .slot(100L)
                                .success(true)
                                .build(),
                        TransactionResponse.builder()
                                .signature("5Kx7aEwMbITTxPage00002")
                                .slot(200L)
                                .success(true)
                                .build()))
                .total(2L)
                .limit(10L)
                .offset(0L)
                .build();
        assertThat(page)
                .usingRecursiveComparison()
                .ignoringCollectionOrder()
                .ignoringFieldsOfTypes(java.time.Instant.class)
                .isEqualTo(expected);
    }

    @Test
    void shouldFilterBySuccessFlag() throws Exception {
        // given
        var success = transactionBuilder()
                .signature(new Signature("5Kx7aEwMbITSuccess0001"))
                .failed(false)
                .build();
        transactionRepository.bulkInsert(List.of(success));
        try (var conn = writePool.getConnection();
                var ps = conn.prepareStatement(
                        "INSERT INTO transactions (signature, slot, success) VALUES (?, ?, ?)")) {
            ps.setString(1, "5Kx7aEwMbITFailed00001");
            ps.setLong(2, 280_000_000L);
            ps.setBoolean(3, false);
            ps.execute();
        }

        // when
        var response = client.send(
                HttpRequest.newBuilder(URI.create(
                                "http://localhost:" + server.port()
                                        + "/api/transactions?success=true"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        // then
        assertThat(response.statusCode()).isEqualTo(200);
        var page = objectMapper.readValue(
                response.body(), new TypeReference<Page<TransactionResponse>>() {});
        var expected = Page.<TransactionResponse>builder()
                .data(List.of(TransactionResponse.builder()
                        .signature("5Kx7aEwMbITSuccess0001")
                        .slot(280_000_000L)
                        .success(true)
                        .build()))
                .total(1L)
                .limit(50L)
                .offset(0L)
                .build();
        assertThat(page)
                .usingRecursiveComparison()
                .ignoringFieldsOfTypes(java.time.Instant.class)
                .isEqualTo(expected);
    }

    @Test
    void shouldReturnTransactionBySignature() throws Exception {
        // given
        var signature = "5Kx7aEwMbITLookup00001";
        var tx = transactionBuilder().signature(new Signature(signature)).slot(555L).build();
        transactionRepository.bulkInsert(List.of(tx));

        // when
        var response = client.send(
                HttpRequest.newBuilder(URI.create(
                                "http://localhost:" + server.port() + "/api/transactions/" + signature))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        // then
        assertThat(response.statusCode()).isEqualTo(200);
        var body = objectMapper.readValue(response.body(), TransactionResponse.class);
        var expected = TransactionResponse.builder()
                .signature(signature)
                .slot(555L)
                .success(true)
                .build();
        assertThat(body)
                .usingRecursiveComparison()
                .ignoringFieldsOfTypes(java.time.Instant.class)
                .isEqualTo(expected);
    }

    @Test
    void shouldReturn404WhenSignatureNotFound() throws Exception {
        // given
        var missingSig = "5Kx7aEwMbITMissing0001";

        // when
        var response = client.send(
                HttpRequest.newBuilder(URI.create(
                                "http://localhost:" + server.port() + "/api/transactions/" + missingSig))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        // then
        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).contains(missingSig);
    }

    @Test
    void shouldReturn400WhenLimitMalformed() throws Exception {
        // given — no data required

        // when
        var response = client.send(
                HttpRequest.newBuilder(URI.create(
                                "http://localhost:" + server.port()
                                        + "/api/transactions?limit=notanumber"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        // then
        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test
    void shouldReturn400WhenOffsetNegative() throws Exception {
        // given — no data required

        // when
        var response = client.send(
                HttpRequest.newBuilder(URI.create(
                                "http://localhost:" + server.port()
                                        + "/api/transactions?offset=-1"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        // then
        assertThat(response.statusCode()).isEqualTo(400);
    }

    @Test
    void shouldReturnTransactionsForSlot() throws Exception {
        // given
        var slot = 77_777L;
        var tx1 = transactionBuilder()
                .signature(new Signature("5Kx7aEwMbITSlot0000001"))
                .slot(slot)
                .build();
        var tx2 = transactionBuilder()
                .signature(new Signature("5Kx7aEwMbITSlot0000002"))
                .slot(slot)
                .build();
        transactionRepository.bulkInsert(List.of(tx1, tx2));

        // when
        var response = client.send(
                HttpRequest.newBuilder(URI.create(
                                "http://localhost:" + server.port() + "/api/slots/" + slot))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        // then
        assertThat(response.statusCode()).isEqualTo(200);
        var list = objectMapper.readValue(
                response.body(), new TypeReference<List<TransactionResponse>>() {});
        assertThat(list).hasSize(2).allSatisfy(tx -> assertThat(tx.slot()).isEqualTo(slot));
    }
}
