package com.stablebridge.prism.infrastructure.persistence;

import static com.stablebridge.prism.fixtures.TransactionFixtures.largeTransferBuilder;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.stablebridge.prism.domain.model.LargeTransfer;
import com.stablebridge.prism.domain.model.Signature;
import com.stablebridge.prism.testutil.SharedPostgresContainer;

class JdbcTransferRepositoryIntegrationTest {

    static DataSource writePool;
    static DataSource readPool;
    static JdbcTransferRepository repository;

    @BeforeAll
    static void setUp() {
        writePool = SharedPostgresContainer.writePool();
        readPool = SharedPostgresContainer.readPool();
        repository = new JdbcTransferRepository(writePool, readPool);
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
    void shouldBatchInsertTransfers() {
        // given
        var transfers = IntStream.range(0, 10)
                .mapToObj(i -> largeTransferBuilder()
                        .signature(
                                new Signature("5Kx7aEwMb" + UUID.randomUUID().toString().substring(0, 8)))
                        .build())
                .toList();

        // when
        repository.bulkInsert(transfers);

        // then
        assertThat(repository.countByMinAmount(BigDecimal.ZERO)).isEqualTo(10);
    }

    @Test
    void shouldFindByMinAmount() {
        // given
        var transfers = List.of(
                largeTransferBuilder()
                        .signature(new Signature("5Kx7aEwMbTransfer0001"))
                        .amount(new BigDecimal("0.5"))
                        .build(),
                largeTransferBuilder()
                        .signature(new Signature("5Kx7aEwMbTransfer0002"))
                        .amount(new BigDecimal("2.0"))
                        .build(),
                largeTransferBuilder()
                        .signature(new Signature("5Kx7aEwMbTransfer0003"))
                        .amount(new BigDecimal("5.0"))
                        .build(),
                largeTransferBuilder()
                        .signature(new Signature("5Kx7aEwMbTransfer0004"))
                        .amount(new BigDecimal("10.0"))
                        .build());
        repository.bulkInsert(transfers);

        // when
        var results = repository.findByMinAmount(new BigDecimal("2.0"), 10, 0);

        // then
        assertThat(results).hasSize(3);
    }

    @Test
    void shouldOrderByAmountDesc() {
        // given
        var transfers = List.of(
                largeTransferBuilder()
                        .signature(new Signature("5Kx7aEwMbTransfer0005"))
                        .amount(new BigDecimal("0.5"))
                        .build(),
                largeTransferBuilder()
                        .signature(new Signature("5Kx7aEwMbTransfer0006"))
                        .amount(new BigDecimal("2.0"))
                        .build(),
                largeTransferBuilder()
                        .signature(new Signature("5Kx7aEwMbTransfer0007"))
                        .amount(new BigDecimal("5.0"))
                        .build(),
                largeTransferBuilder()
                        .signature(new Signature("5Kx7aEwMbTransfer0008"))
                        .amount(new BigDecimal("10.0"))
                        .build());
        repository.bulkInsert(transfers);

        // when
        var results = repository.findByMinAmount(BigDecimal.ZERO, 10, 0);

        // then
        assertThat(results).hasSize(4);
        assertThat(results.getFirst().amount()).isEqualByComparingTo(new BigDecimal("10.0"));
    }

    @Test
    void shouldCountByMinAmount() {
        // given
        var transfers = List.of(
                largeTransferBuilder()
                        .signature(new Signature("5Kx7aEwMbTransfer0009"))
                        .amount(new BigDecimal("0.5"))
                        .build(),
                largeTransferBuilder()
                        .signature(new Signature("5Kx7aEwMbTransfer0010"))
                        .amount(new BigDecimal("2.0"))
                        .build(),
                largeTransferBuilder()
                        .signature(new Signature("5Kx7aEwMbTransfer0011"))
                        .amount(new BigDecimal("5.0"))
                        .build(),
                largeTransferBuilder()
                        .signature(new Signature("5Kx7aEwMbTransfer0012"))
                        .amount(new BigDecimal("10.0"))
                        .build());
        repository.bulkInsert(transfers);

        // when
        var count = repository.countByMinAmount(new BigDecimal("2.0"));

        // then
        assertThat(count).isEqualTo(3);
    }

    @Test
    void shouldHandleEmptyBatch() {
        // given
        var emptyList = List.<LargeTransfer>of();

        // when
        repository.bulkInsert(emptyList);

        // then
        assertThat(repository.countByMinAmount(BigDecimal.ZERO)).isEqualTo(0);
    }
}
