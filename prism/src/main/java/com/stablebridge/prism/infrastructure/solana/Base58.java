package com.stablebridge.prism.infrastructure.solana;

import java.math.BigInteger;
import java.util.Objects;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class Base58 {

    private static final String ALPHABET = "123456789ABCDEFGHJKLMNPQRSTUVWXYZabcdefghijkmnopqrstuvwxyz";
    private static final char ENCODED_ZERO = ALPHABET.charAt(0);
    private static final BigInteger BASE = BigInteger.valueOf(58);
    private static final int[] INDEXES = new int[128];

    static {
        for (var i = 0; i < INDEXES.length; i++) {
            INDEXES[i] = -1;
        }
        for (var i = 0; i < ALPHABET.length(); i++) {
            INDEXES[ALPHABET.charAt(i)] = i;
        }
    }

    public static String encode(byte[] input) {
        Objects.requireNonNull(input, "input must not be null");
        if (input.length == 0) {
            return "";
        }
        var leadingZeros = 0;
        while (leadingZeros < input.length && input[leadingZeros] == 0) {
            leadingZeros++;
        }
        var value = new BigInteger(1, input);
        var sb = new StringBuilder();
        while (value.signum() > 0) {
            var divmod = value.divideAndRemainder(BASE);
            sb.append(ALPHABET.charAt(divmod[1].intValue()));
            value = divmod[0];
        }
        for (var i = 0; i < leadingZeros; i++) {
            sb.append(ENCODED_ZERO);
        }
        return sb.reverse().toString();
    }

    public static byte[] decode(String input) {
        Objects.requireNonNull(input, "input must not be null");
        if (input.isEmpty()) {
            return new byte[0];
        }
        var leadingOnes = 0;
        while (leadingOnes < input.length() && input.charAt(leadingOnes) == ENCODED_ZERO) {
            leadingOnes++;
        }
        var value = BigInteger.ZERO;
        for (var i = leadingOnes; i < input.length(); i++) {
            var c = input.charAt(i);
            if (c >= INDEXES.length || INDEXES[c] < 0) {
                throw new IllegalArgumentException("invalid base58 character '" + c + "' at index " + i);
            }
            value = value.multiply(BASE).add(BigInteger.valueOf(INDEXES[c]));
        }
        var payload = value.signum() == 0 ? new byte[0] : stripSignByte(value.toByteArray());
        var result = new byte[leadingOnes + payload.length];
        System.arraycopy(payload, 0, result, leadingOnes, payload.length);
        return result;
    }

    private static byte[] stripSignByte(byte[] bytes) {
        if (bytes.length > 1 && bytes[0] == 0) {
            var stripped = new byte[bytes.length - 1];
            System.arraycopy(bytes, 1, stripped, 0, stripped.length);
            return stripped;
        }
        return bytes;
    }
}
