package com.stablebridge.prism.infrastructure.persistence;

import static com.stablebridge.prism.fixtures.TransactionFixtures.transactionBuilder;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.stream.IntStream;
import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.stablebridge.prism.domain.model.Signature;
import com.stablebridge.prism.domain.model.SolanaTransaction;
import com.stablebridge.prism.testutil.SharedPostgresContainer;

class CopyTransactionRepositoryIntegrationTest {

    static DataSource writePool;
    static DataSource readPool;
    static CopyTransactionRepository repository;

    @BeforeAll
    static void setUp() {
        writePool = SharedPostgresContainer.writePool();
        readPool = SharedPostgresContainer.readPool();
        repository = new CopyTransactionRepository(writePool, readPool);
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
    void shouldCopyTransactionsToDatabase() {
        // given
        var batch = IntStream.range(0, 100)
                .mapToObj(i -> transactionBuilder()
                        .signature(new Signature("5Kx7aEwMbCopy" + String.format("%04d", i)))
                        .build())
                .toList();

        // when
        repository.bulkInsert(batch);

        // then
        assertThat(repository.countAll()).isEqualTo(100);
    }

    @Test
    void shouldSkipDuplicateSignatures() {
        // given
        var signature = new Signature("5Kx7aEwMbDuplicate000001");
        var tx = transactionBuilder().signature(signature).build();
        repository.bulkInsert(List.of(tx));

        // when
        repository.bulkInsert(List.of(tx));

        // then
        assertThat(repository.countAll()).isEqualTo(1);
    }

    @Test
    void shouldFilterFailedTransactionsFromCopy() {
        // given
        var successful1 = transactionBuilder()
                .signature(new Signature("5Kx7aEwMbSuccess00001"))
                .failed(false)
                .build();
        var successful2 = transactionBuilder()
                .signature(new Signature("5Kx7aEwMbSuccess00002"))
                .failed(false)
                .build();
        var successful3 = transactionBuilder()
                .signature(new Signature("5Kx7aEwMbSuccess00003"))
                .failed(false)
                .build();
        var failed1 = transactionBuilder()
                .signature(new Signature("5Kx7aEwMbFailed000001"))
                .failed(true)
                .build();
        var failed2 = transactionBuilder()
                .signature(new Signature("5Kx7aEwMbFailed000002"))
                .failed(true)
                .build();
        var batch = List.of(successful1, successful2, successful3, failed1, failed2);

        // when
        repository.bulkInsert(batch);

        // then
        assertThat(repository.countAll()).isEqualTo(3);
    }

    @Test
    void shouldFindBySignature() {
        // given
        var signature = new Signature("5Kx7aEwMbFindBySig0001");
        var tx = transactionBuilder().signature(signature).slot(999L).build();
        repository.bulkInsert(List.of(tx));

        // when
        var result = repository.findBySignature(signature);

        // then
        var expected = SolanaTransaction.builder()
                .signature(signature)
                .slot(999L)
                .amount(BigDecimal.ZERO)
                .failed(false)
                .build();
        assertThat(result).isPresent();
        assertThat(result.get()).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldReturnEmptyForMissingSignature() {
        // given
        var signature = new Signature("5Kx7aEwMbNonExistent001");

        // when
        var result = repository.findBySignature(signature);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void shouldFindBySlot() {
        // given
        var slot = 500_000L;
        var tx1 = transactionBuilder()
                .signature(new Signature("5Kx7aEwMbSlotTest00001"))
                .slot(slot)
                .build();
        var tx2 = transactionBuilder()
                .signature(new Signature("5Kx7aEwMbSlotTest00002"))
                .slot(slot)
                .build();
        var tx3 = transactionBuilder()
                .signature(new Signature("5Kx7aEwMbSlotTest00003"))
                .slot(slot)
                .build();
        repository.bulkInsert(List.of(tx1, tx2, tx3));

        // when
        var result = repository.findBySlot(slot);

        // then
        assertThat(result).hasSize(3)
                .allSatisfy(tx -> assertThat(tx.slot()).isEqualTo(slot));
        assertThat(result)
                .extracting(tx -> tx.signature().value())
                .containsExactly(
                        "5Kx7aEwMbSlotTest00001",
                        "5Kx7aEwMbSlotTest00002",
                        "5Kx7aEwMbSlotTest00003");
    }

    @Test
    void shouldLimitResults() {
        // given
        var batch = IntStream.range(0, 10)
                .mapToObj(i -> transactionBuilder()
                        .signature(new Signature("5Kx7aEwMbPage" + String.format("%04d", i)))
                        .build())
                .toList();
        repository.bulkInsert(batch);

        // when
        var result = repository.findAll(3, 0, null);

        // then
        assertThat(result)
                .extracting(tx -> tx.signature().value())
                .containsExactly(
                        "5Kx7aEwMbPage0009",
                        "5Kx7aEwMbPage0008",
                        "5Kx7aEwMbPage0007");
    }

    @Test
    void shouldOffsetResults() {
        // given
        var batch = IntStream.range(0, 10)
                .mapToObj(i -> transactionBuilder()
                        .signature(new Signature("5Kx7aEwMbPage" + String.format("%04d", i)))
                        .build())
                .toList();
        repository.bulkInsert(batch);

        // when
        var result = repository.findAll(10, 3, null);

        // then
        assertThat(result)
                .extracting(tx -> tx.signature().value())
                .containsExactly(
                        "5Kx7aEwMbPage0006",
                        "5Kx7aEwMbPage0005",
                        "5Kx7aEwMbPage0004",
                        "5Kx7aEwMbPage0003",
                        "5Kx7aEwMbPage0002",
                        "5Kx7aEwMbPage0001",
                        "5Kx7aEwMbPage0000");
    }

    @Test
    void shouldFilterBySuccess() throws Exception {
        // given
        var successful = IntStream.range(0, 3)
                .mapToObj(i -> transactionBuilder()
                        .signature(new Signature("5Kx7aEwMbSuccFilter" + String.format("%03d", i)))
                        .build())
                .toList();
        repository.bulkInsert(successful);
        try (var conn = writePool.getConnection();
                var ps = conn.prepareStatement(
                        "INSERT INTO transactions (signature, slot, success) VALUES (?, ?, ?)")) {
            for (var i = 0; i < 2; i++) {
                ps.setString(1, "5Kx7aEwMbFailFilter" + String.format("%03d", i));
                ps.setLong(2, 280_000_000L);
                ps.setBoolean(3, false);
                ps.addBatch();
            }
            ps.executeBatch();
        }

        // when
        var result = repository.findAll(10, 0, true);

        // then
        assertThat(result).hasSize(3);
    }
}
