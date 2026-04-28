package com.stablebridge.prism.fixtures;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;

class EvmTestFixturesTest {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    @Test
    void shouldExposeWellFormedTransactionHash() {
        // when
        var hash = EvmTestFixtures.SOME_TX_HASH;

        // then
        assertThat(hash).hasSize(66).matches("^0x[0-9a-f]{64}$");
    }

    @Test
    void shouldExposeWellFormedBlockHash() {
        // when
        var hash = EvmTestFixtures.SOME_BLOCK_HASH;

        // then
        assertThat(hash).hasSize(66).matches("^0x[0-9a-f]{64}$");
    }

    @Test
    void shouldExposeWellFormedParentHash() {
        // when
        var hash = EvmTestFixtures.SOME_PARENT_HASH;

        // then
        assertThat(hash).hasSize(66).matches("^0x[0-9a-f]{64}$");
    }

    @Test
    void shouldExposeWellFormedAddresses() {
        // when
        var addresses = List.of(
                EvmTestFixtures.SOME_ADDRESS,
                EvmTestFixtures.SOME_FROM_ADDRESS,
                EvmTestFixtures.SOME_TO_ADDRESS,
                EvmTestFixtures.SOME_ERC20_TOKEN_ADDRESS);

        // then
        assertThat(addresses)
                .allSatisfy(addr -> assertThat(addr).hasSize(42).matches("^0x[0-9a-f]{40}$"));
    }

    @Test
    void shouldExposeMainnetChainId() {
        // then
        assertThat(EvmTestFixtures.SOME_CHAIN_ID).isEqualTo(1);
    }

    @Test
    void shouldExposePositiveBlockNumber() {
        // then
        assertThat(EvmTestFixtures.SOME_BLOCK).isPositive();
    }

    @Test
    void shouldExposeStandardErc20TransferTopic() {
        // then
        assertThat(EvmTestFixtures.ERC20_TRANSFER_EVENT_TOPIC)
                .isEqualTo("0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef");
    }

    @Test
    void shouldLoadGetBlockByNumberFixtureWithMatchingBlockHash() throws Exception {
        // when
        var json = EvmTestFixtures.loadFixture("eth_getBlockByNumber_response.json");
        var node = MAPPER.readTree(json);

        // then
        assertThat(node.get("result").get("hash").asText()).isEqualTo(EvmTestFixtures.SOME_BLOCK_HASH);
    }

    @Test
    void shouldLoadGetBlockReceiptsFixtureWithErc20TransferLog() throws Exception {
        // when
        var json = EvmTestFixtures.loadFixture("eth_getBlockReceipts_response.json");
        var node = MAPPER.readTree(json);

        // then
        var transferTopic = node.get("result")
                .get(1)
                .get("logs")
                .get(0)
                .get("topics")
                .get(0)
                .asText();
        assertThat(transferTopic).isEqualTo(EvmTestFixtures.ERC20_TRANSFER_EVENT_TOPIC);
    }

    @Test
    void shouldLoadErc20TransferLogFixtureWithMatchingTopic() throws Exception {
        // when
        var json = EvmTestFixtures.loadFixture("erc20_transfer_log.json");
        var node = MAPPER.readTree(json);

        // then
        assertThat(node.get("topics").get(0).asText())
                .isEqualTo(EvmTestFixtures.ERC20_TRANSFER_EVENT_TOPIC);
    }

    @Test
    void shouldThrowWhenFixtureFileMissing() {
        // when/then
        assertThatThrownBy(() -> EvmTestFixtures.loadFixture("does_not_exist.json"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("does_not_exist.json");
    }

    @Test
    void shouldThrowWhenFilenameIsNull() {
        // when/then
        assertThatThrownBy(() -> EvmTestFixtures.loadFixture(null))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("filename");
    }
}
