package com.stablebridge.prism.infrastructure.grpc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import io.helidon.webclient.grpc.GrpcClientProtocolConfig;
import io.helidon.webclient.http2.Http2ClientProtocolConfig;

class GrpcChannelFactoryTest {

    private static final String SOME_ENDPOINT = "https://localhost:10000";

    @Test
    void shouldReturnChannelForValidEndpoint() {
        // given
        var endpoint = SOME_ENDPOINT;

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
    void shouldConfigureBaseUriFromEndpoint() {
        // given
        var endpoint = SOME_ENDPOINT;

        // when
        var config = GrpcChannelFactory.buildWebClient(endpoint).prototype();

        // then
        assertThat(config.baseUri()).isPresent();
        assertThat(config.baseUri().get().toUri()).hasToString(endpoint + "/");
    }

    @Test
    void shouldConfigureTls() {
        // when
        var config = GrpcChannelFactory.buildWebClient(SOME_ENDPOINT).prototype();

        // then
        assertThat(config.tls().enabled()).isTrue();
        assertThat(config.tls().prototype().trustAll()).isFalse();
    }

    @Test
    void shouldConfigureConnectTimeoutToFifteenSeconds() {
        // when
        var config = GrpcChannelFactory.buildWebClient(SOME_ENDPOINT).prototype();

        // then
        assertThat(config.connectTimeout()).contains(Duration.ofSeconds(15));
    }

    @Test
    void shouldEnableHttpKeepAlive() {
        // when
        var config = GrpcChannelFactory.buildWebClient(SOME_ENDPOINT).prototype();

        // then
        assertThat(config.keepAlive()).isTrue();
    }

    @Test
    void shouldEnableSocketKeepAliveAndTcpNoDelay() {
        // when
        var config = GrpcChannelFactory.buildWebClient(SOME_ENDPOINT).prototype();

        // then
        assertThat(config.socketOptions().socketKeepAlive()).isTrue();
        assertThat(config.socketOptions().tcpNoDelay()).isTrue();
    }

    @Test
    void shouldAttachHttp2ProtocolConfigWithEightMegabyteWindowAndOneMegabyteFrameSize() {
        // when
        var config = GrpcChannelFactory.buildWebClient(SOME_ENDPOINT).prototype();

        // then
        var http2 = config.protocolConfigs().stream()
                .filter(Http2ClientProtocolConfig.class::isInstance)
                .map(Http2ClientProtocolConfig.class::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("Http2ClientProtocolConfig was not attached"));
        assertThat(http2.initialWindowSize()).isEqualTo(8 * 1024 * 1024);
        assertThat(http2.maxFrameSize()).isEqualTo(1024 * 1024);
    }

    @Test
    void shouldAttachGrpcProtocolConfigWithTenSecondHeartbeat() {
        // when
        var config = GrpcChannelFactory.buildWebClient(SOME_ENDPOINT).prototype();

        // then
        var grpc = config.protocolConfigs().stream()
                .filter(GrpcClientProtocolConfig.class::isInstance)
                .map(GrpcClientProtocolConfig.class::cast)
                .findFirst()
                .orElseThrow(() -> new AssertionError("GrpcClientProtocolConfig was not attached"));
        assertThat(grpc.heartbeatPeriod()).isEqualTo(Duration.ofSeconds(10));
    }

    @Test
    void shouldExposeEightMegabyteInitialWindowSize() {
        // when/then
        assertThat(GrpcChannelFactory.INITIAL_WINDOW_SIZE).isEqualTo(8 * 1024 * 1024);
    }

    @Test
    void shouldExposeOneMegabyteMaxFrameSize() {
        // when/then
        assertThat(GrpcChannelFactory.MAX_FRAME_SIZE).isEqualTo(1024 * 1024);
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
