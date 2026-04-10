package com.stablebridge.prism.domain.model;

import java.math.BigDecimal;

import lombok.Builder;

@Builder(toBuilder = true)
public record SolanaTransaction(
        String signature,
        long slot,
        BigDecimal amount,
        boolean failed,
        String memo,
        String from,
        String to
) {}
