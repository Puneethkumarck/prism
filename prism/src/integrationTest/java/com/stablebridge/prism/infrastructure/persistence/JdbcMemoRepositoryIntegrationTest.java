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
        var page1 = repository.findAll(3, 0);
        var page2 = repository.findAll(3, 3);

        // then
        assertThat(page1).hasSize(3);
        assertThat(page2).hasSize(3);
        assertThat(page1)
                .extracting(m -> m.signature().value())
                .doesNotContainAnyElementsOf(
                        page2.stream().map(m -> m.signature().value()).toList());
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

        try (var conn = writePool.getConnection();
                var ps = conn.prepareStatement(
                        "INSERT INTO memos (signature, memo, created_at) VALUES (?, ?, NOW() - INTERVAL '2 seconds')")) {
            ps.setString(1, memo1.signature().value());
            ps.setString(2, memo1.memoText());
            ps.execute();
        }
        try (var conn = writePool.getConnection();
                var ps = conn.prepareStatement(
                        "INSERT INTO memos (signature, memo, created_at) VALUES (?, ?, NOW() - INTERVAL '1 second')")) {
            ps.setString(1, memo2.signature().value());
            ps.setString(2, memo2.memoText());
            ps.execute();
        }
        try (var conn = writePool.getConnection();
                var ps = conn.prepareStatement(
                        "INSERT INTO memos (signature, memo, created_at) VALUES (?, ?, NOW())")) {
            ps.setString(1, memo3.signature().value());
            ps.setString(2, memo3.memoText());
            ps.execute();
        }

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
