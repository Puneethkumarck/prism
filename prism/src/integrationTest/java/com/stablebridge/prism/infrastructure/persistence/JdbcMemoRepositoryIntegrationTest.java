package com.stablebridge.prism.infrastructure.persistence;

import static com.stablebridge.prism.fixtures.TransactionFixtures.memoBuilder;
import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.UUID;
import java.util.stream.IntStream;
import javax.sql.DataSource;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.stablebridge.prism.domain.model.Memo;
import com.stablebridge.prism.domain.model.Signature;
import com.stablebridge.prism.testutil.SharedPostgresContainer;

class JdbcMemoRepositoryIntegrationTest {

    static DataSource writePool;
    static DataSource readPool;
    static JdbcMemoRepository repository;

    @BeforeAll
    static void setUp() {
        writePool = SharedPostgresContainer.writePool();
        readPool = SharedPostgresContainer.readPool();
        repository = new JdbcMemoRepository(writePool, readPool);
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
    void shouldBatchInsertMemos() {
        // given
        var memos = IntStream.range(0, 5)
                .mapToObj(i -> memoBuilder()
                        .signature(
                                new Signature("5Kx7aEwMb" + UUID.randomUUID().toString().substring(0, 8)))
                        .build())
                .toList();

        // when
        repository.bulkInsert(memos);

        // then
        assertThat(repository.countAll()).isEqualTo(5);
    }

    @Test
    void shouldPaginateMemos() {
        // given
        var memos = IntStream.range(0, 10)
                .mapToObj(i -> memoBuilder()
                        .signature(
                                new Signature("5Kx7aEwMb" + UUID.randomUUID().toString().substring(0, 8)))
                        .build())
                .toList();
        repository.bulkInsert(memos);

        // when
        var result = repository.findAll(3, 0);

        // then
        assertThat(result).hasSize(3);
    }

    @Test
    void shouldOrderByCreatedAtDesc() throws Exception {
        // given
        var memo1 = memoBuilder()
                .signature(new Signature("5Kx7aEwMbFirst00001"))
                .memoText("first")
                .build();
        var memo2 = memoBuilder()
                .signature(new Signature("5Kx7aEwMbSecond0001"))
                .memoText("second")
                .build();
        var memo3 = memoBuilder()
                .signature(new Signature("5Kx7aEwMbThird00001"))
                .memoText("third")
                .build();

        repository.bulkInsert(List.of(memo1));
        Thread.sleep(50);
        repository.bulkInsert(List.of(memo2));
        Thread.sleep(50);
        repository.bulkInsert(List.of(memo3));

        // when
        var result = repository.findAll(10, 0);

        // then
        assertThat(result).first().extracting(Memo::signature).isEqualTo(memo3.signature());
    }

    @Test
    void shouldHandleEmptyBatch() {
        // given
        var emptyList = List.<Memo>of();

        // when
        repository.bulkInsert(emptyList);

        // then
        assertThat(repository.countAll()).isEqualTo(0);
    }
}
