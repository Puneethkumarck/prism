package com.stablebridge.prism.domain.model;

import lombok.Builder;

@Builder(toBuilder = true)
public record LargeTransfer(
        String signature,
        long slot,
        double amount
) {}
