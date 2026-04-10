package com.stablebridge.prism.domain.model;

import lombok.Builder;

@Builder(toBuilder = true)
public record IndexerStats(
        long totalTransactions,
        long totalFailed,
        long totalTransfers,
        long totalMemos,
        long totalAccounts
) {

    public IndexerStats {
        if (totalTransactions < 0) {
            throw new IllegalArgumentException("totalTransactions must not be negative");
        }
        if (totalFailed < 0) {
            throw new IllegalArgumentException("totalFailed must not be negative");
        }
        if (totalTransfers < 0) {
            throw new IllegalArgumentException("totalTransfers must not be negative");
        }
        if (totalMemos < 0) {
            throw new IllegalArgumentException("totalMemos must not be negative");
        }
        if (totalAccounts < 0) {
            throw new IllegalArgumentException("totalAccounts must not be negative");
        }
    }
}
