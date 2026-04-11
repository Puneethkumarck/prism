package com.stablebridge.prism.infrastructure.grpc;

import java.net.URI;
import java.net.URISyntaxException;
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
        var parsedEndpoint = parseEndpoint(endpoint);

        var http2Config = Http2ClientProtocolConfig.builder()
                .initialWindowSize(INITIAL_WINDOW_SIZE)
                .maxFrameSize(MAX_FRAME_SIZE)
                .priorKnowledge(true)
                .build();

        var grpcConfig = GrpcClientProtocolConfig.builder()
                .heartbeatPeriod(KEEPALIVE_TIMEOUT)
                .build();

        var tls = Tls.builder()
                .enabled("https".equalsIgnoreCase(parsedEndpoint.getScheme()))
                .build();

        return WebClient.builder()
                .baseUri(parsedEndpoint)
                .tls(tls)
                .connectTimeout(CONNECT_TIMEOUT)
                .keepAlive(true)
                .socketOptions(socket -> socket.socketKeepAlive(true).tcpNoDelay(true))
                .addProtocolConfig(http2Config)
                .addProtocolConfig(grpcConfig)
                .build();
    }

    private static URI parseEndpoint(String endpoint) {
        Objects.requireNonNull(endpoint, "endpoint must not be null");
        var trimmed = endpoint.trim();
        if (trimmed.isEmpty()) {
            throw new IllegalArgumentException("endpoint must not be blank");
        }
        try {
            var uri = new URI(trimmed);
            if (uri.getScheme() == null || uri.getHost() == null) {
                throw new IllegalArgumentException(
                        "endpoint must be an absolute URI with a scheme and host: " + trimmed);
            }
            return uri;
        } catch (URISyntaxException e) {
            throw new IllegalArgumentException("endpoint is not a valid URI: " + trimmed, e);
        }
    }
}
