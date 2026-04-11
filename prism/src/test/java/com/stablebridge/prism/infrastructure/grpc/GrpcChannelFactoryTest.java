package com.stablebridge.prism.infrastructure.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

class GrpcChannelFactoryTest {

    @Test
    void shouldReturnChannelForValidEndpoint() {
        // given
        var endpoint = "https://localhost:10000";

        // when
        var channel = GrpcChannelFactory.create(endpoint);

        // then
        assertThat(channel).isNotNull();
    }

    @Test
    void shouldRejectNullEndpoint() {
        // when/then
        assertThatThrownBy(() -> GrpcChannelFactory.create(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("endpoint");
    }

    @Test
    void shouldRejectBlankEndpoint() {
        // when/then
        assertThatThrownBy(() -> GrpcChannelFactory.create("   "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("endpoint");
    }

    @Test
    void shouldExposeEightMegabyteInitialWindowSize() {
        // when/then
        assertThat(GrpcChannelFactory.INITIAL_WINDOW_SIZE).isEqualTo(8 * 1024 * 1024);
    }

    @Test
    void shouldExposeFifteenSecondConnectTimeout() {
        // when/then
        assertThat(GrpcChannelFactory.CONNECT_TIMEOUT).isEqualTo(Duration.ofSeconds(15));
    }

    @Test
    void shouldExposeTenSecondKeepaliveTimeout() {
        // when/then
        assertThat(GrpcChannelFactory.KEEPALIVE_TIMEOUT).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void shouldExposeSixtyFourMegabyteMaxDecodeMessageSize() {
        // when/then
        assertThat(GrpcChannelFactory.MAX_DECODE_MESSAGE_SIZE).isEqualTo(64 * 1024 * 1024);
    }
}
