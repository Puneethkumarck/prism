package com.stablebridge.prism.api;

import java.time.Instant;

import lombok.Builder;

@Builder(toBuilder = true)
public record AccountResponse(
        String pubkey,
        long lamports,
        long slot,
        boolean executable,
        long rentEpoch,
        Instant createdAt
) {}
