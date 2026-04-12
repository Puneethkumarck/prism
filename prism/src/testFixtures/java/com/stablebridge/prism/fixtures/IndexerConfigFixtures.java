package com.stablebridge.prism.fixtures;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import com.stablebridge.prism.application.config.IndexerConfig;

public final class IndexerConfigFixtures {

    public static final String SOME_DATABASE_URL = "jdbc:postgresql://localhost:5432/prism";
    public static final String SOME_GRPC_ENDPOINT = "https://yellowstone.example.com:443";
    public static final String SOME_RPC_WS_ENDPOINT = "wss://rpc.example.com";
    public static final String SOME_X_TOKEN = "secret-token";
    public static final String SOME_BENCH_LOG = "custom-benchmark.log";
    public static final String SOME_EVM_RPC_HTTP_ENDPOINT = "https://eth-mainnet.example.com";
    public static final String SOME_EVM_RPC_WS_ENDPOINT = "wss://eth-mainnet.example.com/ws";

    private IndexerConfigFixtures() {}

    public static IndexerConfig.IndexerConfigBuilder indexerConfigBuilder() {
        return IndexerConfig.builder()
                .chainMode(IndexerConfig.CHAIN_MODE_SOLANA)
                .streamMode(IndexerConfig.STREAM_MODE_WEBSOCKET)
                .rpcWsEndpoint(SOME_RPC_WS_ENDPOINT)
                .databaseUrl(SOME_DATABASE_URL)
                .benchLog(SOME_BENCH_LOG)
                .apiPort(3000)
                .finalityMode(IndexerConfig.FINALITY_MODE_LATEST)
                .confirmationBlocks(0)
                .maxReorgDepth(0)
                .maxBackfillBlocks(1000)
                .largeTransferThreshold(new BigDecimal("1.0"))
                .blockFetchDelayMs(0);
    }

    public static IndexerConfig.IndexerConfigBuilder evmConfigBuilder() {
        return indexerConfigBuilder()
                .chainMode(IndexerConfig.CHAIN_MODE_ETHEREUM)
                .evmRpcHttpEndpoint(SOME_EVM_RPC_HTTP_ENDPOINT)
                .evmRpcWsEndpoint(SOME_EVM_RPC_WS_ENDPOINT)
                .confirmationBlocks(12)
                .maxReorgDepth(64);
    }

    public static Function<String, String> envOf(Map<String, String> values) {
        var copy = new HashMap<>(values);
        return copy::get;
    }
}
