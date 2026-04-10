package com.stablebridge.prism.domain.model;

import java.util.Objects;

import lombok.Builder;

@Builder(toBuilder = true)
public record Account(
        Pubkey pubkey,
        long lamports,
        long slot,
        boolean executable,
        long rentEpoch
) {

    public Account {
        Objects.requireNonNull(pubkey, "pubkey must not be null");
        if (lamports < 0) {
            throw new IllegalArgumentException("lamports must not be negative");
        }
        if (slot < 0) {
            throw new IllegalArgumentException("slot must not be negative");
        }
        if (rentEpoch < 0) {
            throw new IllegalArgumentException("rentEpoch must not be negative");
        }
    }
}
