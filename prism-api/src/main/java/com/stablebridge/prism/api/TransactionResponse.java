package com.stablebridge.prism.api;

import java.time.Instant;

import lombok.Builder;

@Builder(toBuilder = true)
public record TransactionResponse(
        String signature,
        long slot,
        boolean success,
        Instant createdAt
) {}
