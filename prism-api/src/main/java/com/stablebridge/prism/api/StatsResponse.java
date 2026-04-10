package com.stablebridge.prism.api;

import lombok.Builder;

@Builder(toBuilder = true)
public record StatsResponse(
        long totalTransactions,
        long totalFailed,
        long totalTransfers,
        long totalMemos,
        long totalAccounts
) {}
