package com.stablebridge.prism.domain.model;

import static com.stablebridge.prism.fixtures.TransactionFixtures.largeTransferBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class LargeTransferTest {

    @Test
    void shouldBuildViaBuilder() {
        // given
        var sig = new Signature("sig1");
        var expected = LargeTransfer.builder()
                .signature(sig)
                .slot(100L)
                .amount(new BigDecimal("5.0"))
                .build();

        // when
        var result = LargeTransfer.builder()
                .signature(sig)
                .slot(100L)
                .amount(new BigDecimal("5.0"))
                .build();

        // then
        assertThat(result).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldCreateCopyViaToBuilder() {
        // given
        var original = largeTransferBuilder().build();

        // when
        var copy = original.toBuilder().build();

        // then
        assertThat(copy).usingRecursiveComparison().isEqualTo(original);
    }

    @Test
    void shouldRejectNullSignature() {
        // when/then
        assertThatThrownBy(() -> largeTransferBuilder().signature(null).build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("signature");
    }

    @Test
    void shouldRejectNullAmount() {
        // when/then
        assertThatThrownBy(() -> largeTransferBuilder().amount(null).build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("amount");
    }

    @Test
    void shouldRejectNegativeSlot() {
        // when/then
        assertThatThrownBy(() -> largeTransferBuilder().slot(-1).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("slot");
    }
}
