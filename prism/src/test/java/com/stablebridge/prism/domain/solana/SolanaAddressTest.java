package com.stablebridge.prism.domain.solana;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class SolanaAddressTest {

    @Test
    void shouldTruncateAddressLongerThan16Chars() {
        // given
        var address = "abcdefghijklmnopqrstuv";

        // when
        var result = SolanaAddress.truncate(address);

        // then
        assertThat(result).isEqualTo("abcdefgh...opqrstuv");
    }

    @Test
    void shouldNotTruncateShortAddress() {
        // given
        var address = "abcdefghijklmnop";

        // when
        var result = SolanaAddress.truncate(address);

        // then
        assertThat(result).isEqualTo("abcdefghijklmnop");
    }

    @Test
    void shouldNotTruncateExactlyThresholdLengthAddress() {
        // given
        var address = "1234567890abcdef";

        // when
        var result = SolanaAddress.truncate(address);

        // then
        assertThat(result).isEqualTo("1234567890abcdef");
    }
}
