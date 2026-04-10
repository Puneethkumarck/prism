package com.stablebridge.prism.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import com.stablebridge.prism.testutil.TestDataSourceFactory;

class JdbcStatsRepositoryIntegrationTest {

    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    static DataSource writePool;
    static DataSource readPool;
    static JdbcStatsRepository repository;

    @BeforeAll
    static void setUp() {
        postgres.start();
        writePool = TestDataSourceFactory.create(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword(), false);
        readPool = TestDataSourceFactory.create(
                postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword(), true);
        FlywayMigrator.migrate(writePool);
        repository = new JdbcStatsRepository(readPool);
    }

    @AfterAll
    static void tearDown() {
        postgres.stop();
    }

    @BeforeEach
    void cleanTables() throws Exception {
        try (var conn = writePool.getConnection();
                var stmt = conn.createStatement()) {
            stmt.execute(
                    "TRUNCATE transactions, staging_transactions, failed_transactions, large_transfers, memos, accounts");
            stmt.execute("ANALYZE");
        }
    }

    @Test
    void shouldReturnNonNegativeCounts() {
        // given

        // when
        var stats = repository.getStats();

        // then
        assertThat(stats.totalTransactions()).isGreaterThanOrEqualTo(0);
        assertThat(stats.totalFailed()).isGreaterThanOrEqualTo(0);
        assertThat(stats.totalTransfers()).isGreaterThanOrEqualTo(0);
        assertThat(stats.totalMemos()).isGreaterThanOrEqualTo(0);
        assertThat(stats.totalAccounts()).isGreaterThanOrEqualTo(0);
    }

    @Test
    void shouldReflectInsertedRows() throws Exception {
        // given
        try (var conn = writePool.getConnection();
                var stmt = conn.createStatement()) {
            stmt.execute(
                    "INSERT INTO failed_transactions (signature, slot, error) VALUES ('sig1', 100, 'err1')");
            stmt.execute(
                    "INSERT INTO failed_transactions (signature, slot, error) VALUES ('sig2', 101, 'err2')");
            stmt.execute("INSERT INTO memos (signature, memo) VALUES ('sig3', 'hello')");
            stmt.execute("ANALYZE failed_transactions");
            stmt.execute("ANALYZE memos");
        }

        // when
        var stats = repository.getStats();

        // then
        assertThat(stats.totalFailed()).isGreaterThan(0);
        assertThat(stats.totalMemos()).isGreaterThan(0);
    }
}
