package com.stablebridge.prism.domain.model;

import static com.stablebridge.prism.fixtures.StatsFixtures.batchResultBuilder;
import static org.assertj.core.api.Assertions.assertThat;

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
}
