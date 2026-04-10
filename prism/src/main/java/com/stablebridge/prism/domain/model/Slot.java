package com.stablebridge.prism.domain.model;

import lombok.Builder;

@Builder(toBuilder = true)
public record Slot(long value) {

    public Slot {
        if (value < 0) {
            throw new IllegalArgumentException("slot value must not be negative");
        }
    }
}
