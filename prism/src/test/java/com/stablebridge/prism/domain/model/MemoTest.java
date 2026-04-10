package com.stablebridge.prism.domain.model;

import static com.stablebridge.prism.fixtures.TransactionFixtures.memoBuilder;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class MemoTest {

    @Test
    void shouldBuildViaBuilder() {
        // given
        var expected = Memo.builder()
                .signature("sig1")
                .memoText("hello")
                .build();

        // when
        var result = Memo.builder()
                .signature("sig1")
                .memoText("hello")
                .build();

        // then
        assertThat(result).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldCreateCopyViaToBuilder() {
        // given
        var original = memoBuilder().build();

        // when
        var copy = original.toBuilder().build();

        // then
        assertThat(copy).usingRecursiveComparison().isEqualTo(original);
    }
}
