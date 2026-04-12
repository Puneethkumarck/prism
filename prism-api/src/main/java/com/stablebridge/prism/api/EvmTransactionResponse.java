package com.stablebridge.prism.api;

import java.time.Instant;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import lombok.Builder;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Builder(toBuilder = true)
public record EvmTransactionResponse(
        String txHash,
        long blockNumber,
        String blockHash,
        int transactionIndex,
        String from,
        String to,
        String value,
        boolean status,
        long gasUsed,
        String effectiveGasPrice,
        int type,
        Instant createdAt
) {}
