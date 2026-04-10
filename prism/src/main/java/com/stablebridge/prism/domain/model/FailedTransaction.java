package com.stablebridge.prism.domain.model;

import java.util.Objects;

import lombok.Builder;

@Builder(toBuilder = true)
public record FailedTransaction(
        Signature signature,
        long slot,
        String error
) {

    public FailedTransaction {
        Objects.requireNonNull(signature, "signature must not be null");
        Objects.requireNonNull(error, "error must not be null");
        if (slot < 0) {
            throw new IllegalArgumentException("slot must not be negative");
        }
    }
}
