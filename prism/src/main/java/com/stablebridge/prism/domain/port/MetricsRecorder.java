package com.stablebridge.prism.domain.port;

import com.stablebridge.prism.domain.model.BatchResult;

public interface MetricsRecorder {

    void recordBatch(BatchResult result);

    void recordSlot();

    void incrementReceived();

    void recordAccountsWritten(int count);
}
