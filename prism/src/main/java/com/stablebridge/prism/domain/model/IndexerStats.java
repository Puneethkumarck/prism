package com.stablebridge.prism.domain.model;

import lombok.Builder;

@Builder(toBuilder = true)
public record IndexerStats(
        long totalTransactions,
        long totalFailed,
        long totalTransfers,
        long totalMemos,
        long totalAccounts
) {}
