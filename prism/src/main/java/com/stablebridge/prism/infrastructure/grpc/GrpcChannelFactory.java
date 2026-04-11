package com.stablebridge.prism.infrastructure.grpc;

import java.time.Duration;
import java.util.Objects;

import io.grpc.Channel;
import io.helidon.common.tls.Tls;
import io.helidon.webclient.api.WebClient;
import io.helidon.webclient.grpc.GrpcClient;
import io.helidon.webclient.grpc.GrpcClientProtocolConfig;
import io.helidon.webclient.http2.Http2ClientProtocolConfig;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class GrpcChannelFactory {

    public static final int INITIAL_WINDOW_SIZE = 8 * 1024 * 1024;
    public static final int MAX_FRAME_SIZE = 1024 * 1024;
    public static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);
    public static final Duration KEEPALIVE_TIMEOUT = Duration.ofSeconds(10);
    public static final int MAX_DECODE_MESSAGE_SIZE = 64 * 1024 * 1024;

    public static Channel create(String endpoint) {
        return buildWebClient(endpoint).client(GrpcClient.PROTOCOL).channel();
    }

    static WebClient buildWebClient(String endpoint) {
        Objects.requireNonNull(endpoint, "endpoint must not be null");
        if (endpoint.isBlank()) {
            throw new IllegalArgumentException("endpoint must not be blank");
        }

        var http2Config = Http2ClientProtocolConfig.builder()
                .initialWindowSize(INITIAL_WINDOW_SIZE)
                .maxFrameSize(MAX_FRAME_SIZE)
                .build();

        var grpcConfig = GrpcClientProtocolConfig.builder()
                .heartbeatPeriod(KEEPALIVE_TIMEOUT)
                .build();

        return WebClient.builder()
                .baseUri(endpoint)
                .tls(Tls.builder().build())
                .connectTimeout(CONNECT_TIMEOUT)
                .keepAlive(true)
                .socketOptions(socket -> socket.socketKeepAlive(true).tcpNoDelay(true))
                .addProtocolConfig(http2Config)
                .addProtocolConfig(grpcConfig)
                .build();
    }
}
