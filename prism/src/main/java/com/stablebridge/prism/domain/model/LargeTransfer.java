package com.stablebridge.prism.domain.model;

import java.math.BigDecimal;

import lombok.Builder;

@Builder(toBuilder = true)
public record LargeTransfer(
        String signature,
        long slot,
        BigDecimal amount
) {}
