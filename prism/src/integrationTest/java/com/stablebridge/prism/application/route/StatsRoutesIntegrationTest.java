package com.stablebridge.prism.application.route;

import static com.stablebridge.prism.fixtures.AccountFixtures.accountBuilder;
import static com.stablebridge.prism.fixtures.TransactionFixtures.largeTransferBuilder;
import static com.stablebridge.prism.fixtures.TransactionFixtures.memoBuilder;
import static com.stablebridge.prism.fixtures.TransactionFixtures.transactionBuilder;
import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.UUID;
import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.stablebridge.prism.api.StatsResponse;
import com.stablebridge.prism.application.error.GlobalErrorHandler;
import com.stablebridge.prism.application.mapper.StatsResponseMapper;
import com.stablebridge.prism.domain.model.Pubkey;
import com.stablebridge.prism.domain.model.Signature;
import com.stablebridge.prism.infrastructure.persistence.CopyTransactionRepository;
import com.stablebridge.prism.infrastructure.persistence.JdbcAccountRepository;
import com.stablebridge.prism.infrastructure.persistence.JdbcMemoRepository;
import com.stablebridge.prism.infrastructure.persistence.JdbcStatsRepository;
import com.stablebridge.prism.infrastructure.persistence.JdbcTransferRepository;
import com.stablebridge.prism.testutil.SharedPostgresContainer;

import io.helidon.webserver.WebServer;

class StatsRoutesIntegrationTest {

    static DataSource writePool;
    static DataSource readPool;
    static WebServer server;
    static HttpClient client;
    static ObjectMapper objectMapper;
    static JdbcStatsRepository statsRepository;
    static CopyTransactionRepository transactionRepository;
    static JdbcMemoRepository memoRepository;
    static JdbcTransferRepository transferRepository;
    static JdbcAccountRepository accountRepository;

    @BeforeAll
    static void startServer() {
        writePool = SharedPostgresContainer.writePool();
        readPool = SharedPostgresContainer.readPool();
        statsRepository = new JdbcStatsRepository(readPool);
        transactionRepository = new CopyTransactionRepository(writePool, readPool);
        memoRepository = new JdbcMemoRepository(writePool, readPool);
        transferRepository = new JdbcTransferRepository(writePool, readPool);
        accountRepository = new JdbcAccountRepository(writePool, readPool);
        var mapper = Mappers.getMapper(StatsResponseMapper.class);
        var routes = new StatsRoutes(statsRepository, mapper);
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
    void shouldReturnIndexerStats() throws Exception {
        // given
        var success = transactionBuilder()
                .signature(new Signature("5Kx7aEwMbStatsSucc0001"))
                .failed(false)
                .build();
        transactionRepository.bulkInsert(List.of(success));
        var memo = memoBuilder()
                .signature(new Signature("5Kx7aEwMb" + UUID.randomUUID().toString().substring(0, 8)))
                .memoText("stats memo")
                .build();
        memoRepository.bulkInsert(List.of(memo));
        var transfer = largeTransferBuilder()
                .signature(new Signature("5Kx7aEwMbStatsXfer0001"))
                .build();
        transferRepository.bulkInsert(List.of(transfer));
        var account = accountBuilder()
                .pubkey(new Pubkey("7xKXtg2CStatsAcct0001"))
                .build();
        accountRepository.batchUpsert(List.of(account));
        try (var conn = writePool.getConnection();
                var stmt = conn.createStatement()) {
            stmt.execute("ANALYZE transactions, large_transfers, memos, accounts, failed_transactions");
        }

        // when
        var response = client.send(
                HttpRequest.newBuilder(URI.create("http://localhost:" + server.port() + "/api/stats"))
                        .GET()
                        .build(),
                HttpResponse.BodyHandlers.ofString());

        // then
        assertThat(response.statusCode()).isEqualTo(200);
        var body = objectMapper.readValue(response.body(), StatsResponse.class);
        assertThat(body.totalTransactions()).isGreaterThanOrEqualTo(1L);
        assertThat(body.totalTransfers()).isGreaterThanOrEqualTo(1L);
        assertThat(body.totalMemos()).isGreaterThanOrEqualTo(1L);
        assertThat(body.totalAccounts()).isGreaterThanOrEqualTo(1L);
        assertThat(response.body()).contains("total_transactions");
    }
}
