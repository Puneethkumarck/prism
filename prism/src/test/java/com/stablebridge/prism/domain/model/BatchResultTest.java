package com.stablebridge.prism.domain.model;

import static com.stablebridge.prism.fixtures.StatsFixtures.batchResultBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class BatchResultTest {

    @Test
    void shouldBuildViaBuilder() {
        // given
        var expected = BatchResult.builder()
                .written(10)
                .failed(2)
                .memos(3)
                .transfers(1)
                .build();

        // when
        var result = BatchResult.builder()
                .written(10)
                .failed(2)
                .memos(3)
                .transfers(1)
                .build();

        // then
        assertThat(result).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldCreateCopyViaToBuilder() {
        // given
        var original = batchResultBuilder().build();

        // when
        var copy = original.toBuilder().build();

        // then
        assertThat(copy).usingRecursiveComparison().isEqualTo(original);
    }

    @Test
    void shouldRejectNegativeWritten() {
        // when/then
        assertThatThrownBy(() -> batchResultBuilder().written(-1).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("written");
    }

    @Test
    void shouldRejectNegativeFailed() {
        // when/then
        assertThatThrownBy(() -> batchResultBuilder().failed(-1).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("failed");
    }

    @Test
    void shouldRejectNegativeMemos() {
        // when/then
        assertThatThrownBy(() -> batchResultBuilder().memos(-1).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("memos");
    }

    @Test
    void shouldRejectNegativeTransfers() {
        // when/then
        assertThatThrownBy(() -> batchResultBuilder().transfers(-1).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("transfers");
    }
}
