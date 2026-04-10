package com.stablebridge.prism.domain.model;

import static com.stablebridge.prism.fixtures.TransactionFixtures.failedTransactionBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class FailedTransactionTest {

    @Test
    void shouldBuildViaBuilder() {
        // given
        var sig = new Signature("sig1");
        var expected = FailedTransaction.builder()
                .signature(sig)
                .slot(100L)
                .error("InstructionError")
                .build();

        // when
        var result = FailedTransaction.builder()
                .signature(sig)
                .slot(100L)
                .error("InstructionError")
                .build();

        // then
        assertThat(result).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldCreateCopyViaToBuilder() {
        // given
        var original = failedTransactionBuilder().build();

        // when
        var copy = original.toBuilder().build();

        // then
        assertThat(copy).usingRecursiveComparison().isEqualTo(original);
    }

    @Test
    void shouldRejectNullSignature() {
        // when/then
        assertThatThrownBy(() -> failedTransactionBuilder().signature(null).build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("signature");
    }

    @Test
    void shouldRejectNullError() {
        // when/then
        assertThatThrownBy(() -> failedTransactionBuilder().error(null).build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("error");
    }

    @Test
    void shouldRejectNegativeSlot() {
        // when/then
        assertThatThrownBy(() -> failedTransactionBuilder().slot(-1).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("slot");
    }
}
