package com.stablebridge.prism.domain.model;

import static com.stablebridge.prism.fixtures.TransactionFixtures.largeTransferBuilder;
import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

class LargeTransferTest {

    @Test
    void shouldBuildViaBuilder() {
        // given
        var expected = LargeTransfer.builder()
                .signature("sig1")
                .slot(100L)
                .amount(new BigDecimal("5.0"))
                .build();

        // when
        var result = LargeTransfer.builder()
                .signature("sig1")
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
}
