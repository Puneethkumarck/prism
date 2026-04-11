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
        this.txReceived = Counter.builder("indexer_tx_received")
                .description("Transactions received from the stream adapter")
                .register(registry);
        this.txWritten = Counter.builder("indexer_tx_written")
                .description("Successful transactions written to the database")
                .register(registry);
        this.txFailed = Counter.builder("indexer_tx_failed")
                .description("Failed transactions recorded")
                .register(registry);
        this.txMemo = Counter.builder("indexer_tx_memo")
                .description("Non-failed transactions containing a memo")
                .register(registry);
        this.txTransfer = Counter.builder("indexer_tx_transfer")
                .description("Transactions classified as large transfers")
                .register(registry);
        this.accountsWritten = Counter.builder("indexer_accounts_written")
                .description("Fee-payer accounts upserted to the database")
                .register(registry);
        this.slots = Counter.builder("indexer_slots")
                .description("Slots observed from the stream")
                .register(registry);
        this.batches = Counter.builder("indexer_batches")
                .description("Batches processed by the transaction pipeline")
                .register(registry);
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
        if (count < 0) {
            throw new IllegalArgumentException("count must not be negative");
        }
        accountsWritten.increment(count);
    }
}
