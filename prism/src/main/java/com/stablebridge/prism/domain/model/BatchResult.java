package com.stablebridge.prism.domain.model;

import lombok.Builder;

@Builder(toBuilder = true)
public record BatchResult(long written, long failed, long memos, long transfers) {

    public BatchResult {
        if (written < 0) {
            throw new IllegalArgumentException("written must not be negative");
        }
        if (failed < 0) {
            throw new IllegalArgumentException("failed must not be negative");
        }
        if (memos < 0) {
            throw new IllegalArgumentException("memos must not be negative");
        }
        if (transfers < 0) {
            throw new IllegalArgumentException("transfers must not be negative");
        }
    }
}
