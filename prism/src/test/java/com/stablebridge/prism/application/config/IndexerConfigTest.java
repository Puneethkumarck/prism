package com.stablebridge.prism.application.config;

import static com.stablebridge.prism.fixtures.IndexerConfigFixtures.SOME_BENCH_LOG;
import static com.stablebridge.prism.fixtures.IndexerConfigFixtures.SOME_DATABASE_URL;
import static com.stablebridge.prism.fixtures.IndexerConfigFixtures.SOME_EVM_RPC_HTTP_ENDPOINT;
import static com.stablebridge.prism.fixtures.IndexerConfigFixtures.SOME_EVM_RPC_WS_ENDPOINT;
import static com.stablebridge.prism.fixtures.IndexerConfigFixtures.SOME_GRPC_ENDPOINT;
import static com.stablebridge.prism.fixtures.IndexerConfigFixtures.SOME_RPC_WS_ENDPOINT;
import static com.stablebridge.prism.fixtures.IndexerConfigFixtures.SOME_X_TOKEN;
import static com.stablebridge.prism.fixtures.IndexerConfigFixtures.envOf;
import static com.stablebridge.prism.fixtures.IndexerConfigFixtures.evmConfigBuilder;
import static com.stablebridge.prism.fixtures.IndexerConfigFixtures.indexerConfigBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

class IndexerConfigTest {

    @Nested
    class FromEnv {

        @Test
        void shouldParseAllFieldsFromEnv() {
            // given
            var env = envOf(Map.ofEntries(
                    Map.entry("CHAIN_MODE", "solana"),
                    Map.entry("STREAM_MODE", "grpc"),
                    Map.entry("GRPC_ENDPOINT", SOME_GRPC_ENDPOINT),
                    Map.entry("RPC_WS_ENDPOINT", SOME_RPC_WS_ENDPOINT),
                    Map.entry("DATABASE_URL", SOME_DATABASE_URL),
                    Map.entry("X_TOKEN", SOME_X_TOKEN),
                    Map.entry("CONSOLE_LOG", "false"),
                    Map.entry("BENCH_LOG", SOME_BENCH_LOG),
                    Map.entry("API_PORT", "8080"),
                    Map.entry("FINALITY_MODE", "finalized"),
                    Map.entry("CONFIRMATION_BLOCKS", "6"),
                    Map.entry("MAX_REORG_DEPTH", "32"),
                    Map.entry("MAX_BACKFILL_BLOCKS", "500"),
                    Map.entry("LARGE_TRANSFER_THRESHOLD", "10.5"),
                    Map.entry("BLOCK_FETCH_DELAY_MS", "100")));

            // when
            var config = IndexerConfig.fromEnv(env);

            // then
            var expected = IndexerConfig.builder()
                    .chainMode("solana")
                    .streamMode("grpc")
                    .grpcEndpoint(SOME_GRPC_ENDPOINT)
                    .rpcWsEndpoint(SOME_RPC_WS_ENDPOINT)
                    .databaseUrl(SOME_DATABASE_URL)
                    .xToken(SOME_X_TOKEN)
                    .consoleLog(false)
                    .benchLog(SOME_BENCH_LOG)
                    .apiPort(8080)
                    .finalityMode("finalized")
                    .confirmationBlocks(6)
                    .maxReorgDepth(32)
                    .maxBackfillBlocks(500)
                    .largeTransferThreshold(new BigDecimal("10.5"))
                    .blockFetchDelayMs(100)
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
                    .chainMode("solana")
                    .streamMode("websocket")
                    .grpcEndpoint(SOME_GRPC_ENDPOINT)
                    .rpcWsEndpoint("wss://api.mainnet-beta.solana.com")
                    .databaseUrl(SOME_DATABASE_URL)
                    .xToken(null)
                    .consoleLog(true)
                    .benchLog("benchmark.log")
                    .apiPort(3000)
                    .finalityMode("latest")
                    .confirmationBlocks(0)
                    .maxReorgDepth(0)
                    .maxBackfillBlocks(1000)
                    .largeTransferThreshold(new BigDecimal("1.0"))
                    .blockFetchDelayMs(0)
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

        @Test
        void shouldRejectInvalidLargeTransferThreshold() {
            // given
            var env = envOf(Map.of(
                    "DATABASE_URL", SOME_DATABASE_URL,
                    "LARGE_TRANSFER_THRESHOLD", "not-a-decimal"));

            // when/then
            assertThatThrownBy(() -> IndexerConfig.fromEnv(env))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("LARGE_TRANSFER_THRESHOLD");
        }

        @Test
        void shouldRejectNegativeConfirmationBlocks() {
            // given
            var env = envOf(Map.of(
                    "DATABASE_URL", SOME_DATABASE_URL,
                    "CONFIRMATION_BLOCKS", "-1"));

            // when/then
            assertThatThrownBy(() -> IndexerConfig.fromEnv(env))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("CONFIRMATION_BLOCKS");
        }

        @Test
        void shouldRejectNonIntegerConfirmationBlocks() {
            // given
            var env = envOf(Map.of(
                    "DATABASE_URL", SOME_DATABASE_URL,
                    "CONFIRMATION_BLOCKS", "abc"));

            // when/then
            assertThatThrownBy(() -> IndexerConfig.fromEnv(env))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("CONFIRMATION_BLOCKS");
        }

        @Test
        void shouldRejectNegativeMaxReorgDepth() {
            // given
            var env = envOf(Map.of(
                    "DATABASE_URL", SOME_DATABASE_URL,
                    "MAX_REORG_DEPTH", "-5"));

            // when/then
            assertThatThrownBy(() -> IndexerConfig.fromEnv(env))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("MAX_REORG_DEPTH");
        }

        @Test
        void shouldRejectNegativeMaxBackfillBlocks() {
            // given
            var env = envOf(Map.of(
                    "DATABASE_URL", SOME_DATABASE_URL,
                    "MAX_BACKFILL_BLOCKS", "-100"));

            // when/then
            assertThatThrownBy(() -> IndexerConfig.fromEnv(env))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("MAX_BACKFILL_BLOCKS");
        }

        @Test
        void shouldRejectNegativeBlockFetchDelayMs() {
            // given
            var env = envOf(Map.of(
                    "DATABASE_URL", SOME_DATABASE_URL,
                    "BLOCK_FETCH_DELAY_MS", "-50"));

            // when/then
            assertThatThrownBy(() -> IndexerConfig.fromEnv(env))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("BLOCK_FETCH_DELAY_MS");
        }

        @Test
        void shouldRejectUnknownFinalityMode() {
            // given
            var env = envOf(Map.of(
                    "DATABASE_URL", SOME_DATABASE_URL,
                    "FINALITY_MODE", "safe"));

            // when/then
            assertThatThrownBy(() -> IndexerConfig.fromEnv(env))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("FINALITY_MODE");
        }

        @Test
        void shouldNormalizeFinalityModeCasing() {
            // given
            var env = envOf(Map.of(
                    "DATABASE_URL", SOME_DATABASE_URL,
                    "FINALITY_MODE", "Finalized"));

            // when
            var config = IndexerConfig.fromEnv(env);

            // then
            assertThat(config.finalityMode()).isEqualTo("finalized");
        }
    }

    @Nested
    class ChainMode {

        @Test
        void shouldDefaultToSolanaMode() {
            // given
            var env = envOf(Map.of("DATABASE_URL", SOME_DATABASE_URL));

            // when
            var config = IndexerConfig.fromEnv(env);

            // then
            assertThat(config.chainMode()).isEqualTo("solana");
        }

        @Test
        void shouldRejectUnknownChainMode() {
            // given
            var env = envOf(Map.of(
                    "DATABASE_URL", SOME_DATABASE_URL,
                    "CHAIN_MODE", "avalanche"));

            // when/then
            assertThatThrownBy(() -> IndexerConfig.fromEnv(env))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("CHAIN_MODE");
        }

        @Test
        void shouldNormalizeChainModeCasing() {
            // given
            var env = envOf(Map.ofEntries(
                    Map.entry("DATABASE_URL", SOME_DATABASE_URL),
                    Map.entry("CHAIN_MODE", "Ethereum"),
                    Map.entry("EVM_RPC_HTTP_ENDPOINT", SOME_EVM_RPC_HTTP_ENDPOINT),
                    Map.entry("EVM_RPC_WS_ENDPOINT", SOME_EVM_RPC_WS_ENDPOINT)));

            // when
            var config = IndexerConfig.fromEnv(env);

            // then
            assertThat(config.chainMode()).isEqualTo("ethereum");
        }

        @Test
        void shouldRequireEvmEndpointsWhenChainIsEthereum() {
            // given
            var env = envOf(Map.of(
                    "DATABASE_URL", SOME_DATABASE_URL,
                    "CHAIN_MODE", "ethereum"));

            // when/then
            assertThatThrownBy(() -> IndexerConfig.fromEnv(env))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("EVM_RPC_HTTP_ENDPOINT");
        }

        @Test
        void shouldRequireEvmWsEndpointWhenChainIsEvm() {
            // given
            var env = envOf(Map.of(
                    "DATABASE_URL", SOME_DATABASE_URL,
                    "CHAIN_MODE", "polygon",
                    "EVM_RPC_HTTP_ENDPOINT", SOME_EVM_RPC_HTTP_ENDPOINT));

            // when/then
            assertThatThrownBy(() -> IndexerConfig.fromEnv(env))
                    .isInstanceOf(IllegalStateException.class)
                    .hasMessageContaining("EVM_RPC_WS_ENDPOINT");
        }

        @Test
        void shouldParseEvmConfigForEthereum() {
            // given
            var env = envOf(Map.ofEntries(
                    Map.entry("DATABASE_URL", SOME_DATABASE_URL),
                    Map.entry("CHAIN_MODE", "ethereum"),
                    Map.entry("EVM_RPC_HTTP_ENDPOINT", SOME_EVM_RPC_HTTP_ENDPOINT),
                    Map.entry("EVM_RPC_WS_ENDPOINT", SOME_EVM_RPC_WS_ENDPOINT)));

            // when
            var config = IndexerConfig.fromEnv(env);

            // then
            var expected = IndexerConfig.builder()
                    .chainMode("ethereum")
                    .streamMode("websocket")
                    .grpcEndpoint("")
                    .rpcWsEndpoint("wss://api.mainnet-beta.solana.com")
                    .databaseUrl(SOME_DATABASE_URL)
                    .xToken(null)
                    .consoleLog(true)
                    .benchLog("benchmark.log")
                    .apiPort(3000)
                    .evmRpcHttpEndpoint(SOME_EVM_RPC_HTTP_ENDPOINT)
                    .evmRpcWsEndpoint(SOME_EVM_RPC_WS_ENDPOINT)
                    .finalityMode("latest")
                    .confirmationBlocks(12)
                    .maxReorgDepth(64)
                    .maxBackfillBlocks(1000)
                    .largeTransferThreshold(new BigDecimal("1.0"))
                    .blockFetchDelayMs(0)
                    .build();
            assertThat(config).usingRecursiveComparison().isEqualTo(expected);
        }

        @Test
        void shouldNotRequireEvmEndpointsWhenChainIsSolana() {
            // given
            var env = envOf(Map.of(
                    "DATABASE_URL", SOME_DATABASE_URL,
                    "CHAIN_MODE", "solana"));

            // when
            var config = IndexerConfig.fromEnv(env);

            // then
            assertThat(config.evmRpcHttpEndpoint()).isNull();
            assertThat(config.evmRpcWsEndpoint()).isNull();
        }

        @Test
        void shouldNotRequireGrpcEndpointWhenChainIsEvm() {
            // given
            var env = envOf(Map.ofEntries(
                    Map.entry("DATABASE_URL", SOME_DATABASE_URL),
                    Map.entry("CHAIN_MODE", "ethereum"),
                    Map.entry("STREAM_MODE", "grpc"),
                    Map.entry("EVM_RPC_HTTP_ENDPOINT", SOME_EVM_RPC_HTTP_ENDPOINT),
                    Map.entry("EVM_RPC_WS_ENDPOINT", SOME_EVM_RPC_WS_ENDPOINT)));

            // when
            var config = IndexerConfig.fromEnv(env);

            // then
            assertThat(config.grpcEndpoint()).isEmpty();
        }
    }

    @Nested
    class ChainDefaults {

        @Test
        void shouldUseEthereumDefaultsForConfirmationAndReorg() {
            // when
            var confirmationBlocks = IndexerConfig.defaultConfirmationBlocks("ethereum");
            var maxReorgDepth = IndexerConfig.defaultMaxReorgDepth("ethereum");

            // then
            assertThat(confirmationBlocks).isEqualTo(12);
            assertThat(maxReorgDepth).isEqualTo(64);
        }

        @Test
        void shouldUsePolygonDefaultsForConfirmationAndReorg() {
            // when
            var confirmationBlocks = IndexerConfig.defaultConfirmationBlocks("polygon");
            var maxReorgDepth = IndexerConfig.defaultMaxReorgDepth("polygon");

            // then
            assertThat(confirmationBlocks).isEqualTo(128);
            assertThat(maxReorgDepth).isEqualTo(128);
        }

        @Test
        void shouldUseZeroDefaultsForArbitrum() {
            // when
            var confirmationBlocks = IndexerConfig.defaultConfirmationBlocks("arbitrum");
            var maxReorgDepth = IndexerConfig.defaultMaxReorgDepth("arbitrum");

            // then
            assertThat(confirmationBlocks).isZero();
            assertThat(maxReorgDepth).isZero();
        }

        @Test
        void shouldUseZeroDefaultsForBase() {
            // when
            var confirmationBlocks = IndexerConfig.defaultConfirmationBlocks("base");
            var maxReorgDepth = IndexerConfig.defaultMaxReorgDepth("base");

            // then
            assertThat(confirmationBlocks).isZero();
            assertThat(maxReorgDepth).isZero();
        }

        @Test
        void shouldApplyChainDefaultsWhenEvmEnvVarsOmitted() {
            // given
            var env = envOf(Map.ofEntries(
                    Map.entry("DATABASE_URL", SOME_DATABASE_URL),
                    Map.entry("CHAIN_MODE", "polygon"),
                    Map.entry("EVM_RPC_HTTP_ENDPOINT", SOME_EVM_RPC_HTTP_ENDPOINT),
                    Map.entry("EVM_RPC_WS_ENDPOINT", SOME_EVM_RPC_WS_ENDPOINT)));

            // when
            var config = IndexerConfig.fromEnv(env);

            // then
            assertThat(config.confirmationBlocks()).isEqualTo(128);
            assertThat(config.maxReorgDepth()).isEqualTo(128);
        }
    }

    @Nested
    class HelperMethods {

        @Test
        void shouldReturnTrueForIsEvmModeWhenChainIsEthereum() {
            // given
            var config = evmConfigBuilder().build();

            // then
            assertThat(config.isEvmMode()).isTrue();
            assertThat(config.isSolanaMode()).isFalse();
        }

        @Test
        void shouldReturnTrueForIsSolanaModeWhenChainIsSolana() {
            // given
            var config = indexerConfigBuilder().build();

            // then
            assertThat(config.isSolanaMode()).isTrue();
            assertThat(config.isEvmMode()).isFalse();
        }

        @Test
        void shouldReturnTrueForIsEvmModeWhenChainIsPolygon() {
            // given
            var config = evmConfigBuilder().chainMode("polygon").build();

            // then
            assertThat(config.isEvmMode()).isTrue();
        }

        @Test
        void shouldReturnTrueForIsEvmModeWhenChainIsArbitrum() {
            // given
            var config = evmConfigBuilder().chainMode("arbitrum").build();

            // then
            assertThat(config.isEvmMode()).isTrue();
        }

        @Test
        void shouldReturnTrueForIsEvmModeWhenChainIsBase() {
            // given
            var config = evmConfigBuilder().chainMode("base").build();

            // then
            assertThat(config.isEvmMode()).isTrue();
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
                    .chainMode("solana")
                    .streamMode("websocket")
                    .grpcEndpoint("")
                    .rpcWsEndpoint("wss://api.mainnet-beta.solana.com")
                    .databaseUrl(SOME_DATABASE_URL)
                    .xToken(null)
                    .consoleLog(true)
                    .benchLog("benchmark.log")
                    .apiPort(3000)
                    .finalityMode("latest")
                    .confirmationBlocks(0)
                    .maxReorgDepth(0)
                    .maxBackfillBlocks(1000)
                    .largeTransferThreshold(new BigDecimal("1.0"))
                    .blockFetchDelayMs(0)
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
        void shouldRejectNullChainMode() {
            // when/then
            assertThatThrownBy(() -> indexerConfigBuilder().chainMode(null).build())
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("chainMode");
        }

        @Test
        void shouldRejectBlankChainMode() {
            // when/then
            assertThatThrownBy(() -> indexerConfigBuilder().chainMode("  ").build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("chainMode");
        }

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
        void shouldRejectNullFinalityMode() {
            // when/then
            assertThatThrownBy(() -> indexerConfigBuilder().finalityMode(null).build())
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("finalityMode");
        }

        @Test
        void shouldRejectBlankFinalityMode() {
            // when/then
            assertThatThrownBy(() -> indexerConfigBuilder().finalityMode("  ").build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("finalityMode");
        }

        @Test
        void shouldRejectNullLargeTransferThreshold() {
            // when/then
            assertThatThrownBy(() -> indexerConfigBuilder().largeTransferThreshold(null).build())
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("largeTransferThreshold");
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

        @Test
        void shouldRejectNegativeConfirmationBlocks() {
            // when/then
            assertThatThrownBy(() -> indexerConfigBuilder().confirmationBlocks(-1).build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("confirmationBlocks");
        }

        @Test
        void shouldRejectNegativeMaxReorgDepth() {
            // when/then
            assertThatThrownBy(() -> indexerConfigBuilder().maxReorgDepth(-1).build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("maxReorgDepth");
        }

        @Test
        void shouldRejectNegativeMaxBackfillBlocks() {
            // when/then
            assertThatThrownBy(() -> indexerConfigBuilder().maxBackfillBlocks(-1).build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("maxBackfillBlocks");
        }

        @Test
        void shouldRejectNegativeBlockFetchDelayMs() {
            // when/then
            assertThatThrownBy(() -> indexerConfigBuilder().blockFetchDelayMs(-1).build())
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("blockFetchDelayMs");
        }
    }
}
