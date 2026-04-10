package com.stablebridge.prism.infrastructure.persistence;

import static com.stablebridge.prism.fixtures.TransactionFixtures.failedTransactionBuilder;
import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;
import java.util.List;
import java.util.stream.IntStream;
import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.stablebridge.prism.domain.model.FailedTransaction;
import com.stablebridge.prism.domain.model.Signature;
import com.stablebridge.prism.testutil.SharedPostgresContainer;

class JdbcFailedTransactionRepositoryIntegrationTest {

    static DataSource writePool;
    static JdbcFailedTransactionRepository repository;

    @BeforeAll
    static void setUp() {
        writePool = SharedPostgresContainer.writePool();
        repository = new JdbcFailedTransactionRepository(writePool);
    }

    @BeforeEach
    void cleanTables() throws SQLException {
        try (var conn = writePool.getConnection();
                var stmt = conn.createStatement()) {
            stmt.execute(
                    "TRUNCATE transactions, staging_transactions, failed_transactions, large_transfers, memos, accounts");
        }
    }

    @Test
    void shouldBatchInsertFailedTransactions() throws SQLException {
        // given
        var batch = IntStream.range(0, 50)
                .mapToObj(i -> failedTransactionBuilder()
                        .signature(new Signature("5Kx7aEwMbFailed" + String.format("%05d", i)))
                        .slot(280_000_000L + i)
                        .error("InstructionError-" + i)
                        .build())
                .toList();

        // when
        repository.bulkInsert(batch);

        // then
        try (var conn = writePool.getConnection();
                var stmt = conn.createStatement();
                var rs = stmt.executeQuery("SELECT COUNT(*) FROM failed_transactions")) {
            rs.next();
            assertThat(rs.getLong(1)).isEqualTo(50);
        }

        try (var conn = writePool.getConnection();
                var ps = conn.prepareStatement(
                        "SELECT signature, slot, error FROM failed_transactions WHERE signature = ?")) {
            ps.setString(1, "5Kx7aEwMbFailed00007");
            try (var rs = ps.executeQuery()) {
                assertThat(rs.next()).isTrue();
                assertThat(rs.getString("signature")).isEqualTo("5Kx7aEwMbFailed00007");
                assertThat(rs.getLong("slot")).isEqualTo(280_000_007L);
                assertThat(rs.getString("error")).isEqualTo("InstructionError-7");
            }
        }
    }

    @Test
    void shouldHandleEmptyBatch() throws SQLException {
        // given
        var emptyBatch = List.<FailedTransaction>of();

        // when
        repository.bulkInsert(emptyBatch);

        // then
        try (var conn = writePool.getConnection();
                var stmt = conn.createStatement();
                var rs = stmt.executeQuery("SELECT COUNT(*) FROM failed_transactions")) {
            rs.next();
            assertThat(rs.getLong(1)).isEqualTo(0);
        }
    }
}
