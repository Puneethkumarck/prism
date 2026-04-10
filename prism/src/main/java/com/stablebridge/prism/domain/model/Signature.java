package com.stablebridge.prism.domain.model;

import java.util.Objects;

public record Signature(String value) {

    public Signature {
        Objects.requireNonNull(value, "signature must not be null");
        if (value.isBlank()) {
            throw new IllegalArgumentException("signature must not be blank");
        }
        if (value.length() > 88) {
            throw new IllegalArgumentException("signature must not exceed 88 characters");
        }
    }
}
