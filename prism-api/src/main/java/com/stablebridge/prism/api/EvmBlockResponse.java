package com.stablebridge.prism.api;

import java.time.Instant;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import lombok.Builder;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Builder(toBuilder = true)
public record EvmBlockResponse(
        long blockNumber,
        String blockHash,
        String parentHash,
        Instant timestamp,
        int transactionCount,
        long gasUsed,
        long gasLimit,
        String baseFeePerGas,
        Instant createdAt
) {}
