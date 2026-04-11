package com.stablebridge.prism.api;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import lombok.Builder;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Builder(toBuilder = true)
public record StatsResponse(
        long totalTransactions,
        long totalFailed,
        long totalTransfers,
        long totalMemos,
        long totalAccounts
) {}
