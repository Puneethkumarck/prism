package com.stablebridge.prism.domain.model;

import static com.stablebridge.prism.fixtures.StatsFixtures.indexerStatsBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void shouldRejectNegativeTotalTransactions() {
        // when/then
        assertThatThrownBy(() -> indexerStatsBuilder().totalTransactions(-1).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("totalTransactions");
    }

    @Test
    void shouldRejectNegativeTotalFailed() {
        // when/then
        assertThatThrownBy(() -> indexerStatsBuilder().totalFailed(-1).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("totalFailed");
    }

    @Test
    void shouldRejectNegativeTotalTransfers() {
        // when/then
        assertThatThrownBy(() -> indexerStatsBuilder().totalTransfers(-1).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("totalTransfers");
    }

    @Test
    void shouldRejectNegativeTotalMemos() {
        // when/then
        assertThatThrownBy(() -> indexerStatsBuilder().totalMemos(-1).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("totalMemos");
    }

    @Test
    void shouldRejectNegativeTotalAccounts() {
        // when/then
        assertThatThrownBy(() -> indexerStatsBuilder().totalAccounts(-1).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("totalAccounts");
    }
}
