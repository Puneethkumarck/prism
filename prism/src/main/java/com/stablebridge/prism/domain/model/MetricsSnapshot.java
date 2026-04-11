package com.stablebridge.prism.domain.model;

import lombok.Builder;

@Builder(toBuilder = true)
public record MetricsSnapshot(
        long received,
        long written,
        long failed,
        long memos,
        long transfers,
        long accountsWritten,
        long accountsDropped,
        long batches,
        long slots
) {

    public MetricsSnapshot {
        if (received < 0) {
            throw new IllegalArgumentException("received must not be negative");
        }
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
        if (accountsWritten < 0) {
            throw new IllegalArgumentException("accountsWritten must not be negative");
        }
        if (accountsDropped < 0) {
            throw new IllegalArgumentException("accountsDropped must not be negative");
        }
        if (batches < 0) {
            throw new IllegalArgumentException("batches must not be negative");
        }
        if (slots < 0) {
            throw new IllegalArgumentException("slots must not be negative");
        }
    }

    public long processed() {
        return written + failed;
    }
}
