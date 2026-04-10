package com.stablebridge.prism.domain.model;

import lombok.Builder;

@Builder(toBuilder = true)
public record SolanaTransaction(
        String signature,
        long slot,
        double amount,
        boolean failed,
        String memo,
        String from,
        String to
) {}
