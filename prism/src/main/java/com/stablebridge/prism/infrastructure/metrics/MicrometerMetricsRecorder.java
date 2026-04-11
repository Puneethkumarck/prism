package com.stablebridge.prism.infrastructure.metrics;

import javax.inject.Singleton;

import com.stablebridge.prism.domain.model.BatchResult;
import com.stablebridge.prism.domain.port.MetricsRecorder;

import io.avaje.inject.External;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;

@Singleton
public class MicrometerMetricsRecorder implements MetricsRecorder {

    private final Counter txReceived;
    private final Counter txWritten;
    private final Counter txFailed;
    private final Counter txMemo;
    private final Counter txTransfer;
    private final Counter accountsWritten;
    private final Counter slots;
    private final Counter batches;

    public MicrometerMetricsRecorder(@External MeterRegistry registry) {
        this.txReceived = registry.counter("indexer_tx_received");
        this.txWritten = registry.counter("indexer_tx_written");
        this.txFailed = registry.counter("indexer_tx_failed");
        this.txMemo = registry.counter("indexer_tx_memo");
        this.txTransfer = registry.counter("indexer_tx_transfer");
        this.accountsWritten = registry.counter("indexer_accounts_written");
        this.slots = registry.counter("indexer_slots");
        this.batches = registry.counter("indexer_batches");
    }

    @Override
    public void recordBatch(BatchResult result) {
        txWritten.increment(result.written());
        txFailed.increment(result.failed());
        txMemo.increment(result.memos());
        txTransfer.increment(result.transfers());
        batches.increment();
    }

    @Override
    public void recordSlot() {
        slots.increment();
    }

    @Override
    public void incrementReceived() {
        txReceived.increment();
    }

    @Override
    public void recordAccountsWritten(int count) {
        accountsWritten.increment(count);
    }
}
