package com.stablebridge.prism.infrastructure.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HexFormat;

import org.junit.jupiter.api.Test;

class Base58Test {

    private static final String MEMO_V1_BASE58 = "Memo1UhkJRfHyvLMcVucJwxXeuD728EqVDDwQDxFMNo";
    private static final String MEMO_V1_HEX = "054a5350f85dc882d614a55672788a296ddf1eababd0a60678884932f4eef6a0";
    private static final String MEMO_V2_BASE58 = "MemoSq4gqABAXKb96qnH8TysNcWxMyWCqXgDLGmfcHr";
    private static final String MEMO_V2_HEX = "054a535a992921064d24e87160da387c7c35b5ddbc92bb81e41fa8404105448d";

    @Test
    void shouldEncodeEmptyArrayAsEmptyString() {
        // given
        var input = new byte[0];

        // when
        var result = Base58.encode(input);

        // then
        assertThat(result).isEmpty();
    }

    @Test
    void shouldEncodeSingleZeroByteAsOne() {
        // given
        var input = new byte[] {0};

        // when
        var result = Base58.encode(input);

        // then
        assertThat(result).isEqualTo("1");
    }

    @Test
    void shouldEncodeMultipleLeadingZerosAsMultipleOnes() {
        // given
        var input = new byte[] {0, 0, 0};

        // when
        var result = Base58.encode(input);

        // then
        assertThat(result).isEqualTo("111");
    }

    @Test
    void shouldEncodeKnownSolanaPubkey() {
        // given
        var input = HexFormat.of().parseHex(MEMO_V1_HEX);

        // when
        var result = Base58.encode(input);

        // then
        assertThat(result).isEqualTo(MEMO_V1_BASE58);
    }

    @Test
    void shouldEncodeKnownSolanaPubkeyV2() {
        // given
        var input = HexFormat.of().parseHex(MEMO_V2_HEX);

        // when
        var result = Base58.encode(input);

        // then
        assertThat(result).isEqualTo(MEMO_V2_BASE58);
    }

    @Test
    void shouldRoundTripRandomBytes() {
        // given
        var bytes = new byte[32];
        for (var i = 0; i < 32; i++) {
            bytes[i] = (byte) i;
        }

        // when
        var result = Base58.decode(Base58.encode(bytes));

        // then
        assertThat(result).isEqualTo(bytes);
    }

    @Test
    void shouldDecodeOneAsSingleZeroByte() {
        // given
        var input = "1";

        // when
        var result = Base58.decode(input);

        // then
        assertThat(result).isEqualTo(new byte[] {0});
    }

    @Test
    void shouldDecodeKnownSolanaPubkey() {
        // given
        var input = MEMO_V1_BASE58;

        // when
        var result = Base58.decode(input);

        // then
        assertThat(result).isEqualTo(HexFormat.of().parseHex(MEMO_V1_HEX));
    }

    @Test
    void shouldRejectNullEncodeInput() {
        // when/then
        assertThatThrownBy(() -> Base58.encode(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("input");
    }

    @Test
    void shouldRejectNullDecodeInput() {
        // when/then
        assertThatThrownBy(() -> Base58.decode(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("input");
    }

    @Test
    void shouldRejectInvalidCharacterInDecode() {
        // when/then
        assertThatThrownBy(() -> Base58.decode("0OIl"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("invalid base58 character");
    }
}
