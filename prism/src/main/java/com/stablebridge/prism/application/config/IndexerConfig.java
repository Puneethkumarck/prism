package com.stablebridge.prism.application.config;

import java.util.Objects;
import java.util.Optional;
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

    private static final String DEFAULT_STREAM_MODE = STREAM_MODE_WEBSOCKET;
    private static final String DEFAULT_RPC_WS_ENDPOINT = "wss://api.mainnet-beta.solana.com";
    private static final String DEFAULT_BENCH_LOG = "benchmark.log";
    private static final int DEFAULT_API_PORT = 3000;

    public IndexerConfig {
        Objects.requireNonNull(streamMode, "streamMode must not be null");
        Objects.requireNonNull(rpcWsEndpoint, "rpcWsEndpoint must not be null");
        Objects.requireNonNull(databaseUrl, "databaseUrl must not be null");
        Objects.requireNonNull(benchLog, "benchLog must not be null");
        if (databaseUrl.isBlank()) {
            throw new IllegalArgumentException("databaseUrl must not be blank");
        }
        if (apiPort < 0) {
            throw new IllegalArgumentException("apiPort must not be negative");
        }
        if (apiPort > 65_535) {
            throw new IllegalArgumentException("apiPort must not exceed 65535");
        }
    }

    public static IndexerConfig fromEnv() {
        return fromEnv(System::getenv);
    }

    static IndexerConfig fromEnv(Function<String, String> envSource) {
        Objects.requireNonNull(envSource, "envSource must not be null");
        var databaseUrl = requireEnv(envSource, "DATABASE_URL");
        var streamMode = Optional.ofNullable(envSource.apply("STREAM_MODE"))
                .filter(value -> !value.isBlank())
                .orElse(DEFAULT_STREAM_MODE);
        if (STREAM_MODE_GRPC.equals(streamMode)) {
            requireEnv(envSource, "GRPC_ENDPOINT");
        }
        return IndexerConfig.builder()
                .streamMode(streamMode)
                .grpcEndpoint(Optional.ofNullable(envSource.apply("GRPC_ENDPOINT")).orElse(""))
                .rpcWsEndpoint(Optional.ofNullable(envSource.apply("RPC_WS_ENDPOINT"))
                        .filter(value -> !value.isBlank())
                        .orElse(DEFAULT_RPC_WS_ENDPOINT))
                .databaseUrl(databaseUrl)
                .xToken(envSource.apply("X_TOKEN"))
                .consoleLog(!"false".equalsIgnoreCase(
                        Optional.ofNullable(envSource.apply("CONSOLE_LOG")).orElse("true")))
                .benchLog(Optional.ofNullable(envSource.apply("BENCH_LOG"))
                        .filter(value -> !value.isBlank())
                        .orElse(DEFAULT_BENCH_LOG))
                .apiPort(Integer.parseInt(Optional.ofNullable(envSource.apply("API_PORT"))
                        .filter(value -> !value.isBlank())
                        .orElse(String.valueOf(DEFAULT_API_PORT))))
                .build();
    }

    private static String requireEnv(Function<String, String> envSource, String key) {
        var value = envSource.apply(key);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException(key + " environment variable is required");
        }
        return value;
    }
}
