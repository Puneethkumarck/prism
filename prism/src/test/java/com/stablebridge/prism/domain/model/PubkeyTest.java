package com.stablebridge.prism.domain.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class PubkeyTest {

    @Test
    void shouldCreateWithValidValue() {
        // when
        var result = new Pubkey("7xKXtg2CTestPubkey");

        // then
        assertThat(result.value()).isEqualTo("7xKXtg2CTestPubkey");
    }

    @Test
    void shouldRejectNullValue() {
        // when/then
        assertThatThrownBy(() -> new Pubkey(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("pubkey");
    }

    @Test
    void shouldRejectBlankValue() {
        // when/then
        assertThatThrownBy(() -> new Pubkey("  "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("blank");
    }

    @Test
    void shouldRejectValueExceeding44Characters() {
        // given
        var longValue = "x".repeat(45);

        // when/then
        assertThatThrownBy(() -> new Pubkey(longValue))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("44");
    }

    @Test
    void shouldSupportEqualityByValue() {
        // given
        var pk1 = new Pubkey("7xKXtg2CTestPubkey");
        var pk2 = new Pubkey("7xKXtg2CTestPubkey");

        // then
        assertThat(pk1).isEqualTo(pk2);
    }
}
