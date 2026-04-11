package com.stablebridge.prism.application.route;

import static com.stablebridge.prism.fixtures.TransactionFixtures.memoBuilder;
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
import com.stablebridge.prism.api.MemoResponse;
import com.stablebridge.prism.api.Page;
import com.stablebridge.prism.application.error.GlobalErrorHandler;
import com.stablebridge.prism.application.mapper.MemoResponseMapper;
import com.stablebridge.prism.domain.model.Signature;
import com.stablebridge.prism.infrastructure.persistence.JdbcMemoRepository;
import com.stablebridge.prism.testutil.SharedPostgresContainer;

import io.helidon.webserver.WebServer;

class MemoRoutesIntegrationTest {

    static DataSource writePool;
    static DataSource readPool;
    static WebServer server;
    static HttpClient client;
    static ObjectMapper objectMapper;
    static JdbcMemoRepository memoRepository;

    @BeforeAll
    static void startServer() {
        writePool = SharedPostgresContainer.writePool();
        readPool = SharedPostgresContainer.readPool();
        memoRepository = new JdbcMemoRepository(writePool, readPool);
        var mapper = Mappers.getMapper(MemoResponseMapper.class);
        var routes = new MemoRoutes(memoRepository, mapper);
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
    void shouldReturnPaginatedMemos() throws Exception {
        // given
        var memo1 = memoBuilder()
                .signature(new Signature("5Kx7aEwMbITMemo000001"))
                .memoText("first memo")
                .build();
        var memo2 = memoBuilder()
                .signature(new Signature("5Kx7aEwMbITMemo000002"))
                .memoText("second memo")
                .build();
        memoRepository.bulkInsert(List.of(memo1, memo2));

        // when
        var response = client.send(
                HttpRequest.newBuilder(URI.create(
                                "http://localhost:" + server.port() + "/api/memos?limit=5"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        // then
        assertThat(response.statusCode()).isEqualTo(200);
        var page = objectMapper.readValue(response.body(), new TypeReference<Page<MemoResponse>>() {});
        var expected = Page.<MemoResponse>builder()
                .data(List.of(
                        MemoResponse.builder()
                                .signature("5Kx7aEwMbITMemo000002")
                                .memo("second memo")
                                .build(),
                        MemoResponse.builder()
                                .signature("5Kx7aEwMbITMemo000001")
                                .memo("first memo")
                                .build()))
                .total(2L)
                .limit(5L)
                .offset(0L)
                .build();
        assertThat(page)
                .usingRecursiveComparison()
                .ignoringCollectionOrder()
                .ignoringFieldsOfTypes(java.time.Instant.class)
                .isEqualTo(expected);
    }
}
