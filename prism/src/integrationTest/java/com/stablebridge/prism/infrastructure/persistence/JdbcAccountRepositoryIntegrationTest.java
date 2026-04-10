package com.stablebridge.prism.infrastructure.persistence;

import static com.stablebridge.prism.fixtures.AccountFixtures.accountBuilder;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.stablebridge.prism.domain.model.Account;
import com.stablebridge.prism.domain.model.Pubkey;
import com.stablebridge.prism.testutil.SharedPostgresContainer;

class JdbcAccountRepositoryIntegrationTest {

    static DataSource writePool;
    static DataSource readPool;
    static JdbcAccountRepository repository;

    @BeforeAll
    static void setUp() {
        writePool = SharedPostgresContainer.writePool();
        readPool = SharedPostgresContainer.readPool();
        repository = new JdbcAccountRepository(writePool, readPool);
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
    void shouldInsertNewAccount() {
        // given
        var account = accountBuilder().pubkey(new Pubkey("7xKXtg2CTestPubkey000001")).build();

        // when
        repository.batchUpsert(List.of(account));

        // then
        var result = repository.findByPubkey(account.pubkey());
        assertThat(result).isPresent();
        assertThat(result.get()).usingRecursiveComparison().isEqualTo(account);
    }

    @Test
    void shouldUpdateExistingAccountOnConflict() {
        // given
        var pubkey = new Pubkey("7xKXtg2CTestPubkey000002");
        var original = accountBuilder()
                .pubkey(pubkey)
                .lamports(1_000_000L)
                .slot(100L)
                .build();
        repository.batchUpsert(List.of(original));

        var updated = accountBuilder()
                .pubkey(pubkey)
                .lamports(2_000_000L)
                .slot(200L)
                .build();

        // when
        repository.batchUpsert(List.of(updated));

        // then
        var result = repository.findByPubkey(pubkey);
        assertThat(result).isPresent();
        assertThat(result.get()).usingRecursiveComparison().isEqualTo(updated);
    }

    @Test
    void shouldReturnEmptyForMissingPubkey() {
        // given
        var pubkey = new Pubkey("NonExistentPubkey12345678901");

        // when
        var result = repository.findByPubkey(pubkey);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void shouldHandleEmptyBatch() {
        // given
        var emptyList = List.<Account>of();

        // when
        repository.batchUpsert(emptyList);

        // then
        assertThat(repository.findByPubkey(new Pubkey("AnyPubkey12345678901234"))).isEmpty();
    }
}
