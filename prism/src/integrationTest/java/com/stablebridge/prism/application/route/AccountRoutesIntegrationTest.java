package com.stablebridge.prism.application.route;

import static com.stablebridge.prism.fixtures.AccountFixtures.accountBuilder;
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

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.stablebridge.prism.api.AccountResponse;
import com.stablebridge.prism.application.error.GlobalErrorHandler;
import com.stablebridge.prism.application.mapper.AccountResponseMapper;
import com.stablebridge.prism.domain.model.Pubkey;
import com.stablebridge.prism.infrastructure.persistence.JdbcAccountRepository;
import com.stablebridge.prism.testutil.SharedPostgresContainer;

import io.helidon.webserver.WebServer;

class AccountRoutesIntegrationTest {

    static DataSource writePool;
    static DataSource readPool;
    static WebServer server;
    static HttpClient client;
    static ObjectMapper objectMapper;
    static JdbcAccountRepository accountRepository;

    @BeforeAll
    static void startServer() {
        writePool = SharedPostgresContainer.writePool();
        readPool = SharedPostgresContainer.readPool();
        accountRepository = new JdbcAccountRepository(writePool, readPool);
        var mapper = Mappers.getMapper(AccountResponseMapper.class);
        var routes = new AccountRoutes(accountRepository, mapper);
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
    void shouldReturnAccountByPubkey() throws Exception {
        // given
        var pubkey = "7xKXtg2CITAcct00000001";
        var account = accountBuilder()
                .pubkey(new Pubkey(pubkey))
                .lamports(12_345L)
                .slot(999L)
                .executable(false)
                .rentEpoch(5L)
                .build();
        accountRepository.batchUpsert(List.of(account));

        // when
        var response = client.send(
                HttpRequest.newBuilder(URI.create(
                                "http://localhost:" + server.port() + "/api/accounts/" + pubkey))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        // then
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("rent_epoch");
        var body = objectMapper.readValue(response.body(), AccountResponse.class);
        var expected = AccountResponse.builder()
                .pubkey(pubkey)
                .lamports(12_345L)
                .slot(999L)
                .executable(false)
                .rentEpoch(5L)
                .build();
        assertThat(body).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldReturn404WhenAccountNotFound() throws Exception {
        // given
        var missing = "7xKXtg2CITMissing00001";

        // when
        var response = client.send(
                HttpRequest.newBuilder(URI.create(
                                "http://localhost:" + server.port() + "/api/accounts/" + missing))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        // then
        assertThat(response.statusCode()).isEqualTo(404);
        assertThat(response.body()).contains(missing);
    }
}
