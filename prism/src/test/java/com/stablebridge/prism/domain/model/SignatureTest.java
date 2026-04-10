package com.stablebridge.prism.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class SignatureTest {

    @Test
    void shouldCreateWithValidValue() {
        // when
        var result = new Signature("5Kx7aEwMbTestSig");

        // then
        assertThat(result.value()).isEqualTo("5Kx7aEwMbTestSig");
    }

    @Test
    void shouldRejectNullValue() {
        // when/then
        assertThatThrownBy(() -> new Signature(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("signature");
    }

    @Test
    void shouldRejectBlankValue() {
        // when/then
        assertThatThrownBy(() -> new Signature("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
    }

    @Test
    void shouldRejectValueExceeding88Characters() {
        // given
        var longValue = "x".repeat(89);

        // when/then
        assertThatThrownBy(() -> new Signature(longValue))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("88");
    }

    @Test
    void shouldSupportEqualityByValue() {
        // given
        var sig1 = new Signature("5Kx7aEwMbTestSig");
        var sig2 = new Signature("5Kx7aEwMbTestSig");

        // then
        assertThat(sig1).isEqualTo(sig2);
    }
}
