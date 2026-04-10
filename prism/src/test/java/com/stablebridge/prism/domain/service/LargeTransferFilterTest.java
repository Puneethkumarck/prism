package com.stablebridge.prism.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class LargeTransferFilterTest {

    @Test
    void shouldDetectLargeTransfer() {
        // given
        var amounts = new double[]{1.1, 10.0, 100.0, 1000.0};

        // when / then
        for (var amount : amounts) {
            assertThat(LargeTransferFilter.isLargeTransfer(amount))
                    .as("amount %s should be a large transfer", amount)
                    .isTrue();
        }
    }

    @Test
    void shouldIgnoreSmallTransfer() {
        // given
        var amounts = new double[]{0.0, 0.000005, 0.5};

        // when / then
        for (var amount : amounts) {
            assertThat(LargeTransferFilter.isLargeTransfer(amount))
                    .as("amount %s should not be a large transfer", amount)
                    .isFalse();
        }
    }

    @Test
    void shouldNotDetectExactThreshold() {
        // given
        var amount = LargeTransferFilter.LARGE_TRANSFER_THRESHOLD_SOL;

        // when
        var result = LargeTransferFilter.isLargeTransfer(amount);

        // then
        assertThat(result).isFalse();
    }
}
