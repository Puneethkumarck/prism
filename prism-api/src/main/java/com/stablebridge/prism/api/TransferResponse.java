package com.stablebridge.prism.api;

import java.time.Instant;

import lombok.Builder;

@Builder(toBuilder = true)
public record TransferResponse(
        int id,
        String signature,
        long slot,
        double amount,
        Instant createdAt
) {}
