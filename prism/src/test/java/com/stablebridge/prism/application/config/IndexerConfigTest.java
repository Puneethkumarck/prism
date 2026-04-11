package com.stablebridge.prism.application.config;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.HashMap;
import java.util.Map;
import java.util.function.Function;

import org.junit.jupiter.api.Test;

class IndexerConfigTest {

    private static final String SOME_DATABASE_URL = "jdbc:postgresql://localhost:5432/prism";
    private static final String SOME_GRPC_ENDPOINT = "https://yellowstone.example.com:443";
    private static final String SOME_RPC_WS_ENDPOINT = "wss://rpc.example.com";
    private static final String SOME_X_TOKEN = "secret-token";
    private static final String SOME_BENCH_LOG = "custom-benchmark.log";

    @Test
    void shouldParseAllFieldsFromEnv() {
        // given
        var env = envOf(Map.of(
                "STREAM_MODE", "grpc",
                "GRPC_ENDPOINT", SOME_GRPC_ENDPOINT,
                "RPC_WS_ENDPOINT", SOME_RPC_WS_ENDPOINT,
                "DATABASE_URL", SOME_DATABASE_URL,
                "X_TOKEN", SOME_X_TOKEN,
                "CONSOLE_LOG", "false",
                "BENCH_LOG", SOME_BENCH_LOG,
                "API_PORT", "8080"));

        // when
        var config = IndexerConfig.fromEnv(env);

        // then
        var expected = IndexerConfig.builder()
                .streamMode("grpc")
                .grpcEndpoint(SOME_GRPC_ENDPOINT)
                .rpcWsEndpoint(SOME_RPC_WS_ENDPOINT)
                .databaseUrl(SOME_DATABASE_URL)
                .xToken(SOME_X_TOKEN)
                .consoleLog(false)
                .benchLog(SOME_BENCH_LOG)
                .apiPort(8080)
                .build();
        assertThat(config).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldUseDefaultsWhenOptionalMissing() {
        // given
        var env = envOf(Map.of("DATABASE_URL", SOME_DATABASE_URL));

        // when
        var config = IndexerConfig.fromEnv(env);

        // then
        var expected = IndexerConfig.builder()
                .streamMode("websocket")
                .grpcEndpoint("")
                .rpcWsEndpoint("wss://api.mainnet-beta.solana.com")
                .databaseUrl(SOME_DATABASE_URL)
                .xToken(null)
                .consoleLog(true)
                .benchLog("benchmark.log")
                .apiPort(3000)
                .build();
        assertThat(config).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldDefaultToWebsocketMode() {
        // given
        var env = envOf(Map.of("DATABASE_URL", SOME_DATABASE_URL));

        // when
        var config = IndexerConfig.fromEnv(env);

        // then
        assertThat(config.streamMode()).isEqualTo("websocket");
    }

    @Test
    void shouldFailFastOnMissingDatabaseUrl() {
        // given
        var env = envOf(Map.of());

        // when/then
        assertThatThrownBy(() -> IndexerConfig.fromEnv(env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("DATABASE_URL");
    }

    @Test
    void shouldFailFastOnMissingGrpcEndpointWhenModeIsGrpc() {
        // given
        var env = envOf(Map.of(
                "DATABASE_URL", SOME_DATABASE_URL,
                "STREAM_MODE", "grpc"));

        // when/then
        assertThatThrownBy(() -> IndexerConfig.fromEnv(env))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GRPC_ENDPOINT");
    }

    @Test
    void shouldPreserveGrpcEndpointWhenModeIsWebsocket() {
        // given
        var env = envOf(Map.of(
                "DATABASE_URL", SOME_DATABASE_URL,
                "GRPC_ENDPOINT", SOME_GRPC_ENDPOINT));

        // when
        var config = IndexerConfig.fromEnv(env);

        // then
        var expected = IndexerConfig.builder()
                .streamMode("websocket")
                .grpcEndpoint(SOME_GRPC_ENDPOINT)
                .rpcWsEndpoint("wss://api.mainnet-beta.solana.com")
                .databaseUrl(SOME_DATABASE_URL)
                .xToken(null)
                .consoleLog(true)
                .benchLog("benchmark.log")
                .apiPort(3000)
                .build();
        assertThat(config).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldRejectNullDatabaseUrl() {
        // when/then
        assertThatThrownBy(() -> IndexerConfig.builder()
                        .streamMode("websocket")
                        .rpcWsEndpoint("wss://x")
                        .databaseUrl(null)
                        .benchLog("benchmark.log")
                        .apiPort(3000)
                        .build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("databaseUrl");
    }

    @Test
    void shouldRejectBlankDatabaseUrl() {
        // when/then
        assertThatThrownBy(() -> IndexerConfig.builder()
                        .streamMode("websocket")
                        .rpcWsEndpoint("wss://x")
                        .databaseUrl("   ")
                        .benchLog("benchmark.log")
                        .apiPort(3000)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("databaseUrl");
    }

    @Test
    void shouldRejectNonPositiveApiPort() {
        // when/then
        assertThatThrownBy(() -> IndexerConfig.builder()
                        .streamMode("websocket")
                        .rpcWsEndpoint("wss://x")
                        .databaseUrl(SOME_DATABASE_URL)
                        .benchLog("benchmark.log")
                        .apiPort(0)
                        .build())
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("apiPort");
    }

    @Test
    void shouldRejectNullStreamMode() {
        // when/then
        assertThatThrownBy(() -> IndexerConfig.builder()
                        .streamMode(null)
                        .rpcWsEndpoint("wss://x")
                        .databaseUrl(SOME_DATABASE_URL)
                        .benchLog("benchmark.log")
                        .apiPort(3000)
                        .build())
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("streamMode");
    }

    private static Function<String, String> envOf(Map<String, String> values) {
        var copy = new HashMap<>(values);
        return copy::get;
    }
}
