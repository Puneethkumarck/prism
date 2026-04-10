package com.stablebridge.prism.domain.model;

import static com.stablebridge.prism.fixtures.TransactionFixtures.memoBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class MemoTest {

    @Test
    void shouldBuildViaBuilder() {
        // given
        var sig = new Signature("sig1");
        var expected = Memo.builder()
                .signature(sig)
                .memoText("hello")
                .build();

        // when
        var result = Memo.builder()
                .signature(sig)
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

    @Test
    void shouldRejectNullSignature() {
        // when/then
        assertThatThrownBy(() -> memoBuilder().signature(null).build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("signature");
    }

    @Test
    void shouldRejectNullMemoText() {
        // when/then
        assertThatThrownBy(() -> memoBuilder().memoText(null).build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("memoText");
    }
}
