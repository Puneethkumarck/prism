package com.stablebridge.prism.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class HealthResponseTest {

    @Test
    void shouldBuildWithValidFields() {
        // when
        var response = HealthResponse.builder().status("ok").uptimeSecs(42L).build();

        // then
        var expected = HealthResponse.builder().status("ok").uptimeSecs(42L).build();
        assertThat(response).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldAllowZeroUptime() {
        // when
        var response = HealthResponse.builder().status("ok").uptimeSecs(0L).build();

        // then
        assertThat(response.uptimeSecs()).isZero();
    }

    @Test
    void shouldRejectNegativeUptime() {
        // when/then
        assertThatThrownBy(() -> HealthResponse.builder().status("ok").uptimeSecs(-1L).build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("uptimeSecs");
    }
}
