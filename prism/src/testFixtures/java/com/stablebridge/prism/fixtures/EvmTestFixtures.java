package com.stablebridge.prism.fixtures;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class EvmTestFixtures {

    public static final String SOME_TX_HASH =
            "0x88df016429689c079f3b2f6ad39fa052532c56795b733da78a91ebe6a713944b";
    public static final String SOME_BLOCK_HASH =
            "0x4e3a3754410177e6937ef1f84bba68ea139e8d1a2258c5f85db9f1cd715a1bdd";
    public static final String SOME_PARENT_HASH =
            "0x3a3a3754410177e6937ef1f84bba68ea139e8d1a2258c5f85db9f1cd715a0aaa";
    public static final String SOME_ADDRESS = "0x4675c7e5baafbffbca748158becba61ef3b0a263";
    public static final String SOME_FROM_ADDRESS = "0xd8da6bf26964af9d7eed9e03e53415d37aa96045";
    public static final String SOME_TO_ADDRESS = "0x742d35cc6634c0532925a3b844bc454e4438f44e";
    public static final String SOME_ERC20_TOKEN_ADDRESS =
            "0xa0b86991c6218b36c1d19d4a2e9eb0ce3606eb48";

    public static final long SOME_BLOCK = 19_000_000L;
    public static final int SOME_CHAIN_ID = 1;

    public static final String ERC20_TRANSFER_EVENT_TOPIC =
            "0xddf252ad1be2c89b69c2b068fc378daa952ba7f163c4a11628f55a4df523b3ef";

    private static final String FIXTURE_BASE_PATH = "fixtures/evm/";

    private EvmTestFixtures() {}

    public static String loadFixture(String filename) {
        Objects.requireNonNull(filename, "filename must not be null");
        var resourcePath = FIXTURE_BASE_PATH + filename;
        try (var stream = EvmTestFixtures.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IllegalArgumentException("EVM fixture not found on classpath: " + resourcePath);
            }
            return new String(stream.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read EVM fixture: " + resourcePath, e);
        }
    }
}
