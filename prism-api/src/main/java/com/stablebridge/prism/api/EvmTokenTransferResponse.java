package com.stablebridge.prism.api;

import java.time.Instant;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import lombok.Builder;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Builder(toBuilder = true)
public record EvmTokenTransferResponse(
        long id,
        String txHash,
        long blockNumber,
        int logIndex,
        String tokenAddress,
        String from,
        String to,
        String value,
        Instant createdAt
) {}
