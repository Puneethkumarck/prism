package com.stablebridge.prism.infrastructure.metrics;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.stablebridge.prism.domain.model.BatchResult;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class MicrometerMetricsRecorderTest {

    @Test
    void shouldIncrementAllCountersOnRecordBatch() {
        // given
        var registry = new SimpleMeterRegistry();
        var recorder = new MicrometerMetricsRecorder(registry);
        var batch = BatchResult.builder().written(5).failed(2).memos(1).transfers(1).build();

        // when
        recorder.recordBatch(batch);

        // then
        var expected = new CounterSnapshot(0, 5, 2, 1, 1, 0, 0, 1);
        var actual = snapshot(registry);
        assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldIncrementSlotCounter() {
        // given
        var registry = new SimpleMeterRegistry();
        var recorder = new MicrometerMetricsRecorder(registry);

        // when
        recorder.recordSlot();
        recorder.recordSlot();
        recorder.recordSlot();

        // then
        var expected = new CounterSnapshot(0, 0, 0, 0, 0, 0, 3, 0);
        var actual = snapshot(registry);
        assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldIncrementReceivedCounter() {
        // given
        var registry = new SimpleMeterRegistry();
        var recorder = new MicrometerMetricsRecorder(registry);

        // when
        recorder.incrementReceived();
        recorder.incrementReceived();
        recorder.incrementReceived();
        recorder.incrementReceived();
        recorder.incrementReceived();

        // then
        var expected = new CounterSnapshot(5, 0, 0, 0, 0, 0, 0, 0);
        var actual = snapshot(registry);
        assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldIncrementAccountsWrittenCounter() {
        // given
        var registry = new SimpleMeterRegistry();
        var recorder = new MicrometerMetricsRecorder(registry);

        // when
        recorder.recordAccountsWritten(10);

        // then
        var expected = new CounterSnapshot(0, 0, 0, 0, 0, 10, 0, 0);
        var actual = snapshot(registry);
        assertThat(actual).usingRecursiveComparison().isEqualTo(expected);
    }

    private CounterSnapshot snapshot(MeterRegistry registry) {
        return new CounterSnapshot(
                registry.counter("indexer_tx_received").count(),
                registry.counter("indexer_tx_written").count(),
                registry.counter("indexer_tx_failed").count(),
                registry.counter("indexer_tx_memo").count(),
                registry.counter("indexer_tx_transfer").count(),
                registry.counter("indexer_accounts_written").count(),
                registry.counter("indexer_slots").count(),
                registry.counter("indexer_batches").count());
    }

    private record CounterSnapshot(
            double received,
            double written,
            double failed,
            double memo,
            double transfer,
            double accountsWritten,
            double slots,
            double batches) {}
}
