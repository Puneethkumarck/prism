package com.stablebridge.prism.domain.solana;

public final class SolanaAddress {

    public static final int LAMPORTS_PER_SOL_SCALE = 9;
    public static final int TRUNCATION_THRESHOLD = 16;
    public static final int TRUNCATION_PREFIX = 8;
    public static final int TRUNCATION_SUFFIX = 8;

    private SolanaAddress() {}

    public static String truncate(String address) {
        if (address.length() > TRUNCATION_THRESHOLD) {
            return address.substring(0, TRUNCATION_PREFIX)
                    + "..."
                    + address.substring(address.length() - TRUNCATION_SUFFIX);
        }
        return address;
    }
}
