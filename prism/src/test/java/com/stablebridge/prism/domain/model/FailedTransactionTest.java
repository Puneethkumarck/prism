package com.stablebridge.prism.domain.model;

import static com.stablebridge.prism.fixtures.TransactionFixtures.failedTransactionBuilder;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class FailedTransactionTest {

    @Test
    void shouldBuildViaBuilder() {
        // given
        var expected = FailedTransaction.builder()
                .signature("sig1")
                .slot(100L)
                .error("InstructionError")
                .build();

        // when
        var result = FailedTransaction.builder()
                .signature("sig1")
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
}
