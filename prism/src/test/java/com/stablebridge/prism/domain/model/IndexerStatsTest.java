package com.stablebridge.prism.domain.model;

import static com.stablebridge.prism.fixtures.StatsFixtures.indexerStatsBuilder;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class IndexerStatsTest {

    @Test
    void shouldBuildViaBuilder() {
        // given
        var expected = IndexerStats.builder()
                .totalTransactions(1000)
                .totalFailed(50)
                .totalTransfers(200)
                .totalMemos(100)
                .totalAccounts(500)
                .build();

        // when
        var result = IndexerStats.builder()
                .totalTransactions(1000)
                .totalFailed(50)
                .totalTransfers(200)
                .totalMemos(100)
                .totalAccounts(500)
                .build();

        // then
        assertThat(result).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldCreateCopyViaToBuilder() {
        // given
        var original = indexerStatsBuilder().build();

        // when
        var copy = original.toBuilder().build();

        // then
        assertThat(copy).usingRecursiveComparison().isEqualTo(original);
    }
}
