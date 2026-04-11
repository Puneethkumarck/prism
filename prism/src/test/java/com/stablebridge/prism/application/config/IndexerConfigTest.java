package com.stablebridge.prism.application.config;

import static com.stablebridge.prism.fixtures.IndexerConfigFixtures.SOME_BENCH_LOG;
import static com.stablebridge.prism.fixtures.IndexerConfigFixtures.SOME_DATABASE_URL;
import static com.stablebridge.prism.fixtures.IndexerConfigFixtures.SOME_GRPC_ENDPOINT;
import static com.stablebridge.prism.fixtures.IndexerConfigFixtures.SOME_RPC_WS_ENDPOINT;
import static com.stablebridge.prism.fixtures.IndexerConfigFixtures.SOME_X_TOKEN;
import static com.stablebridge.prism.fixtures.IndexerConfigFixtures.envOf;
import static com.stablebridge.prism.fixtures.IndexerConfigFixtures.indexerConfigBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Map;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class IndexerConfigTest {

    @Nested
    class FromEnv {

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
        void shouldTreatBlankXTokenAsAbsent() {
            // given
            var env = envOf(Map.of(
                    "DATABASE_URL", SOME_DATABASE_URL,
                    "X_TOKEN", "   "));

            // when
            var config = IndexerConfig.fromEnv(env);

            // then
            assertThat(config.xToken()).isNull();
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
        void shouldFailFastOnNonIntegerApiPort() {
            // given
            var env = envOf(Map.of(
                    "DATABASE_URL", SOME_DATABASE_URL,
                    "API_PORT", "not-a-number"));

            // when/then
            assertThatThrownBy(() -> IndexerConfig.fromEnv(env))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("API_PORT");
        }

        @Test
        void shouldRejectUnknownStreamMode() {
            // given
            var env = envOf(Map.of(
                    "DATABASE_URL", SOME_DATABASE_URL,
                    "STREAM_MODE", "kafka"));

            // when/then
            assertThatThrownBy(() -> IndexerConfig.fromEnv(env))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("STREAM_MODE");
        }

        @Test
        void shouldNormalizeStreamModeCasing() {
            // given
            var env = envOf(Map.of(
                    "DATABASE_URL", SOME_DATABASE_URL,
                    "STREAM_MODE", "WebSocket"));

            // when
            var config = IndexerConfig.fromEnv(env);

            // then
            assertThat(config.streamMode()).isEqualTo("websocket");
        }

        @Test
        void shouldRejectInsecureGrpcEndpoint() {
            // given
            var env = envOf(Map.of(
                    "DATABASE_URL", SOME_DATABASE_URL,
                    "STREAM_MODE", "grpc",
                    "GRPC_ENDPOINT", "http://yellowstone.example.com:443"));

            // when/then
            assertThatThrownBy(() -> IndexerConfig.fromEnv(env))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("GRPC_ENDPOINT");
        }

        @Test
        void shouldAllowHttpGrpcEndpointOnLocalhost() {
            // given
            var env = envOf(Map.of(
                    "DATABASE_URL", SOME_DATABASE_URL,
                    "STREAM_MODE", "grpc",
                    "GRPC_ENDPOINT", "http://localhost:10000"));

            // when
            var config = IndexerConfig.fromEnv(env);

            // then
            assertThat(config.grpcEndpoint()).isEqualTo("http://localhost:10000");
        }

        @Test
        void shouldRejectMalformedGrpcEndpoint() {
            // given
            var env = envOf(Map.of(
                    "DATABASE_URL", SOME_DATABASE_URL,
                    "STREAM_MODE", "grpc",
                    "GRPC_ENDPOINT", "::::not-a-uri"));

            // when/then
            assertThatThrownBy(() -> IndexerConfig.fromEnv(env))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("GRPC_ENDPOINT");
        }
    }

    @Nested
    class Defaults {

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
    }

    @Nested
    class Validation {

        @Test
        void shouldRejectNullStreamMode() {
            // when/then
            assertThatThrownBy(() -> indexerConfigBuilder().streamMode(null).build())
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("streamMode");
        }

        @Test
        void shouldRejectBlankStreamMode() {
            // when/then
            assertThatThrownBy(() -> indexerConfigBuilder().streamMode("  ").build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("streamMode");
        }

        @Test
        void shouldRejectNullRpcWsEndpoint() {
            // when/then
            assertThatThrownBy(() -> indexerConfigBuilder().rpcWsEndpoint(null).build())
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("rpcWsEndpoint");
        }

        @Test
        void shouldRejectBlankRpcWsEndpoint() {
            // when/then
            assertThatThrownBy(() -> indexerConfigBuilder().rpcWsEndpoint("  ").build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("rpcWsEndpoint");
        }

        @Test
        void shouldRejectNullDatabaseUrl() {
            // when/then
            assertThatThrownBy(() -> indexerConfigBuilder().databaseUrl(null).build())
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("databaseUrl");
        }

        @Test
        void shouldRejectBlankDatabaseUrl() {
            // when/then
            assertThatThrownBy(() -> indexerConfigBuilder().databaseUrl("   ").build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("databaseUrl");
        }

        @Test
        void shouldRejectNullBenchLog() {
            // when/then
            assertThatThrownBy(() -> indexerConfigBuilder().benchLog(null).build())
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("benchLog");
        }

        @Test
        void shouldRejectBlankBenchLog() {
            // when/then
            assertThatThrownBy(() -> indexerConfigBuilder().benchLog("  ").build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("benchLog");
        }

        @Test
        void shouldRejectNegativeApiPort() {
            // when/then
            assertThatThrownBy(() -> indexerConfigBuilder().apiPort(-1).build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("apiPort");
        }

        @Test
        void shouldRejectApiPortAboveMax() {
            // when/then
            assertThatThrownBy(() -> indexerConfigBuilder().apiPort(65_536).build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("apiPort");
        }

        @Test
        void shouldAllowEphemeralApiPort() {
            // when
            var config = indexerConfigBuilder().apiPort(0).build();

            // then
            assertThat(config.apiPort()).isZero();
        }

        @Test
        void shouldAllowMaxApiPort() {
            // when
            var config = indexerConfigBuilder().apiPort(65_535).build();

            // then
            assertThat(config.apiPort()).isEqualTo(65_535);
        }
    }
}
