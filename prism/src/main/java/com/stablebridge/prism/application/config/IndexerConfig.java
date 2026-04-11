package com.stablebridge.prism.application.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import lombok.Builder;

@Builder(toBuilder = true)
public record IndexerConfig(
        String streamMode,
        String grpcEndpoint,
        String rpcWsEndpoint,
        String databaseUrl,
        String xToken,
        boolean consoleLog,
        String benchLog,
        int apiPort) {

    public static final String STREAM_MODE_GRPC = "grpc";
    public static final String STREAM_MODE_WEBSOCKET = "websocket";

    private static final Set<String> ALLOWED_STREAM_MODES = Set.of(STREAM_MODE_GRPC, STREAM_MODE_WEBSOCKET);
    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "::1");
    private static final String DEFAULT_STREAM_MODE = STREAM_MODE_WEBSOCKET;
    private static final String DEFAULT_RPC_WS_ENDPOINT = "wss://api.mainnet-beta.solana.com";
    private static final String DEFAULT_BENCH_LOG = "benchmark.log";
    private static final int DEFAULT_API_PORT = 3000;
    private static final int MAX_API_PORT = 65_535;

    public IndexerConfig {
        Objects.requireNonNull(streamMode, "streamMode must not be null");
        Objects.requireNonNull(rpcWsEndpoint, "rpcWsEndpoint must not be null");
        Objects.requireNonNull(databaseUrl, "databaseUrl must not be null");
        Objects.requireNonNull(benchLog, "benchLog must not be null");
        if (streamMode.isBlank()) {
            throw new IllegalArgumentException("streamMode must not be blank");
        }
        if (rpcWsEndpoint.isBlank()) {
            throw new IllegalArgumentException("rpcWsEndpoint must not be blank");
        }
        if (databaseUrl.isBlank()) {
            throw new IllegalArgumentException("databaseUrl must not be blank");
        }
        if (benchLog.isBlank()) {
            throw new IllegalArgumentException("benchLog must not be blank");
        }
        if (apiPort < 0) {
            throw new IllegalArgumentException("apiPort must not be negative");
        }
        if (apiPort > MAX_API_PORT) {
            throw new IllegalArgumentException("apiPort must not exceed " + MAX_API_PORT);
        }
    }

    public static IndexerConfig fromEnv() {
        return fromEnv(System::getenv);
    }

    static IndexerConfig fromEnv(Function<String, String> envSource) {
        Objects.requireNonNull(envSource, "envSource must not be null");
        var databaseUrl = requireEnv(envSource, "DATABASE_URL");
        var streamMode = parseStreamMode(nonBlank(envSource, "STREAM_MODE").orElse(DEFAULT_STREAM_MODE));
        if (STREAM_MODE_GRPC.equals(streamMode)) {
            validateGrpcEndpoint(requireEnv(envSource, "GRPC_ENDPOINT"));
        }
        return IndexerConfig.builder()
                .streamMode(streamMode)
                .grpcEndpoint(nonBlank(envSource, "GRPC_ENDPOINT").orElse(""))
                .rpcWsEndpoint(nonBlank(envSource, "RPC_WS_ENDPOINT").orElse(DEFAULT_RPC_WS_ENDPOINT))
                .databaseUrl(databaseUrl)
                .xToken(nonBlank(envSource, "X_TOKEN").orElse(null))
                .consoleLog(!"false".equalsIgnoreCase(nonBlank(envSource, "CONSOLE_LOG").orElse("true")))
                .benchLog(nonBlank(envSource, "BENCH_LOG").orElse(DEFAULT_BENCH_LOG))
                .apiPort(parseApiPort(nonBlank(envSource, "API_PORT").orElse(String.valueOf(DEFAULT_API_PORT))))
                .build();
    }

    private static Optional<String> nonBlank(Function<String, String> envSource, String key) {
        return Optional.ofNullable(envSource.apply(key)).filter(value -> !value.isBlank());
    }

    private static String requireEnv(Function<String, String> envSource, String key) {
        return nonBlank(envSource, key)
                .orElseThrow(() -> new IllegalStateException(key + " environment variable is required"));
    }

    private static String parseStreamMode(String raw) {
        var normalized = raw.toLowerCase(Locale.ROOT);
        if (!ALLOWED_STREAM_MODES.contains(normalized)) {
            throw new IllegalStateException(
                    "STREAM_MODE must be one of " + ALLOWED_STREAM_MODES + ", got: " + raw);
        }
        return normalized;
    }

    private static int parseApiPort(String raw) {
        try {
            return Integer.parseInt(raw);
        } catch (NumberFormatException e) {
            throw new IllegalStateException("API_PORT must be a valid integer, got: " + raw, e);
        }
    }

    private static void validateGrpcEndpoint(String endpoint) {
        URI uri;
        try {
            uri = new URI(endpoint);
        } catch (URISyntaxException e) {
            throw new IllegalStateException("GRPC_ENDPOINT must be a valid URI, got: " + endpoint, e);
        }
        var scheme = Optional.ofNullable(uri.getScheme())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new IllegalStateException(
                        "GRPC_ENDPOINT must include a URI scheme (https, or http for localhost), got: " + endpoint));
        var host = Optional.ofNullable(uri.getHost())
                .map(value -> value.toLowerCase(Locale.ROOT))
                .orElse("");
        var isLocal = LOCAL_HOSTS.contains(host);
        if ("https".equals(scheme)) {
            return;
        }
        if ("http".equals(scheme) && isLocal) {
            return;
        }
        throw new IllegalStateException(
                "GRPC_ENDPOINT must use https (http allowed only for localhost), got: " + endpoint);
    }
}
