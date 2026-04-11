package com.stablebridge.prism.application.route;

import static com.stablebridge.prism.fixtures.TransactionFixtures.largeTransferBuilder;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
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
import com.stablebridge.prism.api.TransferResponse;
import com.stablebridge.prism.application.error.GlobalErrorHandler;
import com.stablebridge.prism.application.mapper.TransferResponseMapper;
import com.stablebridge.prism.domain.model.Signature;
import com.stablebridge.prism.infrastructure.persistence.JdbcTransferRepository;
import com.stablebridge.prism.testutil.SharedPostgresContainer;

import io.helidon.webserver.WebServer;

class TransferRoutesIntegrationTest {

    static DataSource writePool;
    static DataSource readPool;
    static WebServer server;
    static HttpClient client;
    static ObjectMapper objectMapper;
    static JdbcTransferRepository transferRepository;

    @BeforeAll
    static void startServer() {
        writePool = SharedPostgresContainer.writePool();
        readPool = SharedPostgresContainer.readPool();
        transferRepository = new JdbcTransferRepository(writePool, readPool);
        var mapper = Mappers.getMapper(TransferResponseMapper.class);
        var routes = new TransferRoutes(transferRepository, mapper);
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
    void shouldFilterByMinAmount() throws Exception {
        // given
        var smallTransfer = largeTransferBuilder()
                .signature(new Signature("5Kx7aEwMbITXferSmall01"))
                .amount(new BigDecimal("1.5"))
                .build();
        var largeTransfer = largeTransferBuilder()
                .signature(new Signature("5Kx7aEwMbITXferLarge01"))
                .amount(new BigDecimal("7.0"))
                .build();
        var veryLargeTransfer = largeTransferBuilder()
                .signature(new Signature("5Kx7aEwMbITXferHuge001"))
                .amount(new BigDecimal("100.0"))
                .build();
        transferRepository.bulkInsert(List.of(smallTransfer, largeTransfer, veryLargeTransfer));

        // when
        var response = client.send(
                HttpRequest.newBuilder(URI.create(
                                "http://localhost:" + server.port()
                                        + "/api/transfers?min_amount=5.0"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        // then
        assertThat(response.statusCode()).isEqualTo(200);
        var page = objectMapper.readValue(
                response.body(), new TypeReference<Page<TransferResponse>>() {});
        assertThat(page.total()).isEqualTo(2L);
        assertThat(page.data())
                .hasSize(2)
                .extracting(TransferResponse::signature)
                .containsExactly("5Kx7aEwMbITXferHuge001", "5Kx7aEwMbITXferLarge01");
    }
}
