package com.stablebridge.prism.domain.model;

import java.util.Objects;

public record Pubkey(String value) {

    public Pubkey {
        Objects.requireNonNull(value, "pubkey must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("pubkey must not be blank");
        }
        if (value.length() > 44) {
            throw new IllegalArgumentException("pubkey must not exceed 44 characters");
        }
    }
}
