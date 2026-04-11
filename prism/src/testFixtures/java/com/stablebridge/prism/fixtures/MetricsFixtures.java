package com.stablebridge.prism.fixtures;

import com.stablebridge.prism.domain.model.MetricsSnapshot;

public final class MetricsFixtures {

    private MetricsFixtures() {}

    public static MetricsSnapshot.MetricsSnapshotBuilder metricsSnapshotBuilder() {
        return MetricsSnapshot.builder()
                .received(1_635L)
                .written(968L)
                .failed(631L)
                .memos(7L)
                .transfers(121L)
                .accountsWritten(0L)
                .batches(30L)
                .slots(8L);
    }

    public static final MetricsSnapshot SOME_METRICS_SNAPSHOT = metricsSnapshotBuilder().build();

    public static final MetricsSnapshot EMPTY_METRICS_SNAPSHOT = MetricsSnapshot.builder()
            .received(0L)
            .written(0L)
            .failed(0L)
            .memos(0L)
            .transfers(0L)
            .accountsWritten(0L)
            .batches(0L)
            .slots(0L)
            .build();
}
