package com.stablebridge.prism.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

import lombok.Builder;

@Builder(toBuilder = true)
public record LargeTransfer(
        Signature signature,
        long slot,
        BigDecimal amount
) {

    public LargeTransfer {
        Objects.requireNonNull(signature, "signature must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        if (slot < 0) {
            throw new IllegalArgumentException("slot must not be negative");
        }
    }
}
