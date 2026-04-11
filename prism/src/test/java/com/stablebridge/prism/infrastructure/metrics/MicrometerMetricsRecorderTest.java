package com.stablebridge.prism.infrastructure.metrics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import org.junit.jupiter.api.Test;

import com.stablebridge.prism.domain.model.BatchResult;
import com.stablebridge.prism.domain.model.MetricsSnapshot;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

class MicrometerMetricsRecorderTest {

    private static final String TX_RECEIVED = "indexer_tx_received";
    private static final String TX_WRITTEN = "indexer_tx_written";
    private static final String TX_FAILED = "indexer_tx_failed";
    private static final String TX_MEMO = "indexer_tx_memo";
    private static final String TX_TRANSFER = "indexer_tx_transfer";
    private static final String ACCOUNTS_WRITTEN = "indexer_accounts_written";
    private static final String SLOTS = "indexer_slots";
    private static final String BATCHES = "indexer_batches";

    private static final List<String> ALL_COUNTERS =
            List.of(TX_RECEIVED, TX_WRITTEN, TX_FAILED, TX_MEMO, TX_TRANSFER, ACCOUNTS_WRITTEN, SLOTS, BATCHES);

    @Test
    void shouldIncrementAllCountersOnRecordBatch() {
        // given
        var registry = new SimpleMeterRegistry();
        var recorder = new MicrometerMetricsRecorder(registry);
        var batch = BatchResult.builder().written(5).failed(2).memos(1).transfers(1).build();

        // when
        recorder.recordBatch(batch);

        // then
        var expected = zeroedSnapshot();
        expected.put(TX_WRITTEN, 5.0);
        expected.put(TX_FAILED, 2.0);
        expected.put(TX_MEMO, 1.0);
        expected.put(TX_TRANSFER, 1.0);
        expected.put(BATCHES, 1.0);
        assertThat(snapshot(registry)).usingRecursiveComparison().isEqualTo(expected);
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
        var expected = zeroedSnapshot();
        expected.put(SLOTS, 3.0);
        assertThat(snapshot(registry)).usingRecursiveComparison().isEqualTo(expected);
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
        var expected = zeroedSnapshot();
        expected.put(TX_RECEIVED, 5.0);
        assertThat(snapshot(registry)).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldIncrementAccountsWrittenCounter() {
        // given
        var registry = new SimpleMeterRegistry();
        var recorder = new MicrometerMetricsRecorder(registry);

        // when
        recorder.recordAccountsWritten(10);

        // then
        var expected = zeroedSnapshot();
        expected.put(ACCOUNTS_WRITTEN, 10.0);
        assertThat(snapshot(registry)).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldRejectNegativeAccountsWrittenCount() {
        // given
        var registry = new SimpleMeterRegistry();
        var recorder = new MicrometerMetricsRecorder(registry);

        // when/then
        assertThatThrownBy(() -> recorder.recordAccountsWritten(-1))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("count");
    }

    @Test
    void shouldExposeCounterSnapshot() {
        // given
        var registry = new SimpleMeterRegistry();
        var recorder = new MicrometerMetricsRecorder(registry);
        recorder.incrementReceived();
        recorder.incrementReceived();
        recorder.recordBatch(BatchResult.builder().written(5).failed(2).memos(1).transfers(1).build());
        recorder.recordAccountsWritten(3);
        recorder.recordSlot();

        // when
        var snapshot = recorder.snapshot();

        // then
        var expected = MetricsSnapshot.builder()
                .received(2L)
                .written(5L)
                .failed(2L)
                .memos(1L)
                .transfers(1L)
                .accountsWritten(3L)
                .batches(1L)
                .slots(1L)
                .build();
        assertThat(snapshot).usingRecursiveComparison().isEqualTo(expected);
    }

    private Map<String, Double> snapshot(MeterRegistry registry) {
        var result = new LinkedHashMap<String, Double>();
        ALL_COUNTERS.forEach(name -> {
            var counter = registry.find(name).counter();
            Objects.requireNonNull(counter, "Counter not registered: " + name);
            result.put(name, counter.count());
        });
        return result;
    }

    private Map<String, Double> zeroedSnapshot() {
        var result = new LinkedHashMap<String, Double>();
        ALL_COUNTERS.forEach(name -> result.put(name, 0.0));
        return result;
    }
}
