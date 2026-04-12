package com.stablebridge.prism.api;

import java.time.Instant;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import lombok.Builder;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Builder(toBuilder = true)
public record EvmAccountResponse(
        String address,
        long blockNumber,
        long transactionCount,
        Instant firstSeen,
        Instant lastSeen,
        Instant createdAt
) {}
