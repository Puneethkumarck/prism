package com.stablebridge.prism.infrastructure.grpc;

import java.time.Duration;
import java.util.Objects;

import io.grpc.Channel;
import io.helidon.common.socket.SocketOptions;
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
    public static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(15);
    public static final Duration KEEPALIVE_TIMEOUT = Duration.ofSeconds(10);
    public static final int MAX_DECODE_MESSAGE_SIZE = 64 * 1024 * 1024;

    public static Channel create(String endpoint) {
        Objects.requireNonNull(endpoint, "endpoint must not be null");
        if (endpoint.isBlank()) {
            throw new IllegalArgumentException("endpoint must not be blank");
        }

        var tls = Tls.builder().build();

        var socketOptions = SocketOptions.builder()
                .socketKeepAlive(true)
                .build();

        var http2Config = Http2ClientProtocolConfig.builder()
                .initialWindowSize(INITIAL_WINDOW_SIZE)
                .build();

        var grpcConfig = GrpcClientProtocolConfig.builder()
                .heartbeatPeriod(KEEPALIVE_TIMEOUT)
                .build();

        var webClient = WebClient.builder()
                .baseUri(endpoint)
                .tls(tls)
                .connectTimeout(CONNECT_TIMEOUT)
                .keepAlive(true)
                .socketOptions(socketOptions)
                .maxInMemoryEntity(MAX_DECODE_MESSAGE_SIZE)
                .addProtocolConfig(http2Config)
                .addProtocolConfig(grpcConfig)
                .build();

        return webClient.client(GrpcClient.PROTOCOL).channel();
    }
}
