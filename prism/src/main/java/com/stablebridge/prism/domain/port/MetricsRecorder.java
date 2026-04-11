package com.stablebridge.prism.domain.port;

import com.stablebridge.prism.domain.model.BatchResult;
import com.stablebridge.prism.domain.model.MetricsSnapshot;

public interface MetricsRecorder {

    void recordBatch(BatchResult result);

    void recordSlot();

    void incrementReceived();

    void recordAccountsWritten(int count);

    void recordAccountDropped();

    MetricsSnapshot snapshot();
}
