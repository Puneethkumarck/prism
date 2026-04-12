package com.stablebridge.prism.application.config;

import java.math.BigDecimal;
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
        String chainMode,
        String streamMode,
        String grpcEndpoint,
        String rpcWsEndpoint,
        String databaseUrl,
        String xToken,
        boolean consoleLog,
        String benchLog,
        int apiPort,
        String evmRpcHttpEndpoint,
        String evmRpcWsEndpoint,
        String finalityMode,
        int confirmationBlocks,
        int maxReorgDepth,
        long maxBackfillBlocks,
        BigDecimal largeTransferThreshold,
        long blockFetchDelayMs) {

    public static final String CHAIN_MODE_SOLANA = "solana";
    public static final String CHAIN_MODE_ETHEREUM = "ethereum";
    public static final String CHAIN_MODE_POLYGON = "polygon";
    public static final String CHAIN_MODE_ARBITRUM = "arbitrum";
    public static final String CHAIN_MODE_BASE = "base";

    public static final String STREAM_MODE_GRPC = "grpc";
    public static final String STREAM_MODE_WEBSOCKET = "websocket";

    public static final String FINALITY_MODE_LATEST = "latest";
    public static final String FINALITY_MODE_FINALIZED = "finalized";

    private static final Set<String> ALLOWED_CHAIN_MODES =
            Set.of(CHAIN_MODE_SOLANA, CHAIN_MODE_ETHEREUM, CHAIN_MODE_POLYGON, CHAIN_MODE_ARBITRUM, CHAIN_MODE_BASE);
    private static final Set<String> ALLOWED_STREAM_MODES = Set.of(STREAM_MODE_GRPC, STREAM_MODE_WEBSOCKET);
    private static final Set<String> ALLOWED_FINALITY_MODES =
            Set.of(FINALITY_MODE_LATEST, FINALITY_MODE_FINALIZED);
    private static final Set<String> LOCAL_HOSTS = Set.of("localhost", "127.0.0.1", "::1");

    private static final String DEFAULT_CHAIN_MODE = CHAIN_MODE_SOLANA;
    private static final String DEFAULT_STREAM_MODE = STREAM_MODE_WEBSOCKET;
    private static final String DEFAULT_RPC_WS_ENDPOINT = "wss://api.mainnet-beta.solana.com";
    private static final String DEFAULT_BENCH_LOG = "benchmark.log";
    private static final int DEFAULT_API_PORT = 3000;
    private static final int MAX_API_PORT = 65_535;
    private static final String DEFAULT_FINALITY_MODE = FINALITY_MODE_LATEST;
    private static final long DEFAULT_MAX_BACKFILL_BLOCKS = 1000;
    private static final String DEFAULT_LARGE_TRANSFER_THRESHOLD = "1.0";

    public IndexerConfig {
        Objects.requireNonNull(chainMode, "chainMode must not be null");
        Objects.requireNonNull(streamMode, "streamMode must not be null");
        Objects.requireNonNull(rpcWsEndpoint, "rpcWsEndpoint must not be null");
        Objects.requireNonNull(databaseUrl, "databaseUrl must not be null");
        Objects.requireNonNull(benchLog, "benchLog must not be null");
        Objects.requireNonNull(finalityMode, "finalityMode must not be null");
        Objects.requireNonNull(largeTransferThreshold, "largeTransferThreshold must not be null");
        if (chainMode.isBlank()) {
            throw new IllegalArgumentException("chainMode must not be blank");
        }
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
        if (finalityMode.isBlank()) {
            throw new IllegalArgumentException("finalityMode must not be blank");
        }
        if (apiPort < 0) {
            throw new IllegalArgumentException("apiPort must not be negative");
        }
        if (apiPort > MAX_API_PORT) {
            throw new IllegalArgumentException("apiPort must not exceed " + MAX_API_PORT);
        }
        if (confirmationBlocks < 0) {
            throw new IllegalArgumentException("confirmationBlocks must not be negative");
        }
        if (maxReorgDepth < 0) {
            throw new IllegalArgumentException("maxReorgDepth must not be negative");
        }
        if (maxBackfillBlocks < 0) {
            throw new IllegalArgumentException("maxBackfillBlocks must not be negative");
        }
        if (blockFetchDelayMs < 0) {
            throw new IllegalArgumentException("blockFetchDelayMs must not be negative");
        }
    }

    public boolean isEvmMode() {
        return !CHAIN_MODE_SOLANA.equals(chainMode);
    }

    public boolean isSolanaMode() {
        return CHAIN_MODE_SOLANA.equals(chainMode);
    }

    public static IndexerConfig fromEnv() {
        return fromEnv(System::getenv);
    }

    static IndexerConfig fromEnv(Function<String, String> envSource) {
        Objects.requireNonNull(envSource, "envSource must not be null");
        var databaseUrl = requireEnv(envSource, "DATABASE_URL");
        var chainMode = parseChainMode(nonBlank(envSource, "CHAIN_MODE").orElse(DEFAULT_CHAIN_MODE));
        var streamMode = parseStreamMode(nonBlank(envSource, "STREAM_MODE").orElse(DEFAULT_STREAM_MODE));
        if (CHAIN_MODE_SOLANA.equals(chainMode) && STREAM_MODE_GRPC.equals(streamMode)) {
            validateGrpcEndpoint(requireEnv(envSource, "GRPC_ENDPOINT"));
        }
        String evmRpcHttpEndpoint = null;
        String evmRpcWsEndpoint = null;
        if (!CHAIN_MODE_SOLANA.equals(chainMode)) {
            evmRpcHttpEndpoint = requireEnv(envSource, "EVM_RPC_HTTP_ENDPOINT");
            evmRpcWsEndpoint = requireEnv(envSource, "EVM_RPC_WS_ENDPOINT");
        }
        var finalityMode =
                parseFinalityMode(nonBlank(envSource, "FINALITY_MODE").orElse(DEFAULT_FINALITY_MODE));
        var confirmationBlocks = parseNonNegativeInt(
                nonBlank(envSource, "CONFIRMATION_BLOCKS")
                        .orElse(String.valueOf(defaultConfirmationBlocks(chainMode))),
                "CONFIRMATION_BLOCKS");
        var maxReorgDepth = parseNonNegativeInt(
                nonBlank(envSource, "MAX_REORG_DEPTH")
                        .orElse(String.valueOf(defaultMaxReorgDepth(chainMode))),
                "MAX_REORG_DEPTH");
        var maxBackfillBlocks = parseNonNegativeLong(
                nonBlank(envSource, "MAX_BACKFILL_BLOCKS")
                        .orElse(String.valueOf(DEFAULT_MAX_BACKFILL_BLOCKS)),
                "MAX_BACKFILL_BLOCKS");
        var largeTransferThreshold = parseBigDecimal(
                nonBlank(envSource, "LARGE_TRANSFER_THRESHOLD").orElse(DEFAULT_LARGE_TRANSFER_THRESHOLD),
                "LARGE_TRANSFER_THRESHOLD");
        var blockFetchDelayMs = parseNonNegativeLong(
                nonBlank(envSource, "BLOCK_FETCH_DELAY_MS").orElse("0"), "BLOCK_FETCH_DELAY_MS");
        return IndexerConfig.builder()
                .chainMode(chainMode)
                .streamMode(streamMode)
                .grpcEndpoint(nonBlank(envSource, "GRPC_ENDPOINT").orElse(""))
                .rpcWsEndpoint(nonBlank(envSource, "RPC_WS_ENDPOINT").orElse(DEFAULT_RPC_WS_ENDPOINT))
                .databaseUrl(databaseUrl)
                .xToken(nonBlank(envSource, "X_TOKEN").orElse(null))
                .consoleLog(!"false".equalsIgnoreCase(nonBlank(envSource, "CONSOLE_LOG").orElse("true")))
                .benchLog(nonBlank(envSource, "BENCH_LOG").orElse(DEFAULT_BENCH_LOG))
                .apiPort(parseApiPort(
                        nonBlank(envSource, "API_PORT").orElse(String.valueOf(DEFAULT_API_PORT))))
                .evmRpcHttpEndpoint(evmRpcHttpEndpoint)
                .evmRpcWsEndpoint(evmRpcWsEndpoint)
                .finalityMode(finalityMode)
                .confirmationBlocks(confirmationBlocks)
                .maxReorgDepth(maxReorgDepth)
                .maxBackfillBlocks(maxBackfillBlocks)
                .largeTransferThreshold(largeTransferThreshold)
                .blockFetchDelayMs(blockFetchDelayMs)
                .build();
    }

    static int defaultConfirmationBlocks(String chainMode) {
        return switch (chainMode) {
            case CHAIN_MODE_ETHEREUM -> 12;
            case CHAIN_MODE_POLYGON -> 128;
            case CHAIN_MODE_ARBITRUM, CHAIN_MODE_BASE -> 0;
            default -> 0;
        };
    }

    static int defaultMaxReorgDepth(String chainMode) {
        return switch (chainMode) {
            case CHAIN_MODE_ETHEREUM -> 64;
            case CHAIN_MODE_POLYGON -> 128;
            case CHAIN_MODE_ARBITRUM, CHAIN_MODE_BASE -> 0;
            default -> 0;
        };
    }

    private static Optional<String> nonBlank(Function<String, String> envSource, String key) {
        return Optional.ofNullable(envSource.apply(key)).filter(value -> !value.isBlank());
    }

    private static String requireEnv(Function<String, String> envSource, String key) {
        return nonBlank(envSource, key)
                .orElseThrow(() -> new IllegalStateException(key + " environment variable is required"));
    }

    private static String parseChainMode(String raw) {
        var normalized = raw.toLowerCase(Locale.ROOT);
        if (!ALLOWED_CHAIN_MODES.contains(normalized)) {
            throw new IllegalStateException(
                    "CHAIN_MODE must be one of " + ALLOWED_CHAIN_MODES + ", got: " + raw);
        }
        return normalized;
    }

    private static String parseStreamMode(String raw) {
        var normalized = raw.toLowerCase(Locale.ROOT);
        if (!ALLOWED_STREAM_MODES.contains(normalized)) {
            throw new IllegalStateException(
                    "STREAM_MODE must be one of " + ALLOWED_STREAM_MODES + ", got: " + raw);
        }
        return normalized;
    }

    private static String parseFinalityMode(String raw) {
        var normalized = raw.toLowerCase(Locale.ROOT);
        if (!ALLOWED_FINALITY_MODES.contains(normalized)) {
            throw new IllegalStateException(
                    "FINALITY_MODE must be one of " + ALLOWED_FINALITY_MODES + ", got: " + raw);
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

    private static int parseNonNegativeInt(String raw, String name) {
        try {
            var value = Integer.parseInt(raw);
            if (value < 0) {
                throw new IllegalStateException(name + " must not be negative, got: " + raw);
            }
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalStateException(name + " must be a valid integer, got: " + raw, e);
        }
    }

    private static long parseNonNegativeLong(String raw, String name) {
        try {
            var value = Long.parseLong(raw);
            if (value < 0) {
                throw new IllegalStateException(name + " must not be negative, got: " + raw);
            }
            return value;
        } catch (NumberFormatException e) {
            throw new IllegalStateException(name + " must be a valid long, got: " + raw, e);
        }
    }

    private static BigDecimal parseBigDecimal(String raw, String name) {
        try {
            return new BigDecimal(raw);
        } catch (NumberFormatException e) {
            throw new IllegalStateException(name + " must be a valid decimal, got: " + raw, e);
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
                        "GRPC_ENDPOINT must include a URI scheme (https, or http for localhost), got: "
                                + endpoint));
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
