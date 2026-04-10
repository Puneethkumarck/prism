package com.stablebridge.prism.domain.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class LargeTransferFilterTest {

    @ParameterizedTest
    @ValueSource(doubles = {1.1, 10.0, 100.0, 1000.0})
    void shouldDetectLargeTransfer(double amount) {
        // when
        var result = LargeTransferFilter.isLargeTransfer(amount);

        // then
        assertThat(result).isTrue();
    }

    @ParameterizedTest
    @ValueSource(doubles = {0.0, 0.000005, 0.5})
    void shouldIgnoreSmallTransfer(double amount) {
        // when
        var result = LargeTransferFilter.isLargeTransfer(amount);

        // then
        assertThat(result).isFalse();
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
