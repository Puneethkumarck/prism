package com.stablebridge.prism.application.route;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.stablebridge.prism.api.HealthResponse;

class HealthRoutesTest {

    @Test
    void shouldReportOkWithComputedUptime() {
        // given
        var start = 1_000L;
        var routes = new HealthRoutes(start);

        // when
        var result = routes.currentHealth(1_042L);

        // then
        var expected = HealthResponse.builder()
                .status("ok")
                .uptimeSecs(42L)
                .build();
        assertThat(result).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldReportZeroUptimeAtStart() {
        // given
        var start = 500L;
        var routes = new HealthRoutes(start);

        // when
        var result = routes.currentHealth(500L);

        // then
        var expected = HealthResponse.builder()
                .status("ok")
                .uptimeSecs(0L)
                .build();
        assertThat(result).usingRecursiveComparison().isEqualTo(expected);
    }
}
