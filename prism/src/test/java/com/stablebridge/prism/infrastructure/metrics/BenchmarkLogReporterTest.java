package com.stablebridge.prism.infrastructure.metrics;

import static com.stablebridge.prism.fixtures.MetricsFixtures.EMPTY_METRICS_SNAPSHOT;
import static com.stablebridge.prism.fixtures.MetricsFixtures.metricsSnapshotBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.offset;
import static org.mockito.BDDMockito.given;

import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stablebridge.prism.domain.port.MetricsRecorder;

@ExtendWith(MockitoExtension.class)
class BenchmarkLogReporterTest {

    private static final Instant FIXED_INSTANT = Instant.parse("2026-04-06T19:54:15Z");
    private static final String EXPECTED_LINE =
            "2026-04-06T19:54:15Z |  319.00 |      1635 |       968 |       631 |     39% |     7 |   121 |         0 |      30 |     8";

    @Mock
    private MetricsRecorder metricsRecorder;

    @Test
    void shouldFormatLogLineCorrectly() {
        // given
        var snapshot = metricsSnapshotBuilder().build();

        // when
        var line = BenchmarkLogReporter.formatLine(FIXED_INSTANT, 319.0, snapshot);

        // then
        assertThat(line).isEqualTo(EXPECTED_LINE);
    }

    @Test
    void shouldComputeTpsDelta() {
        // given
        var previousProcessed = 100L;
        var currentProcessed = 200L;
        var intervalSecs = 300L;

        // when
        var tps = BenchmarkLogReporter.computeTps(previousProcessed, currentProcessed, intervalSecs);

        // then
        assertThat(tps).isCloseTo(0.33, offset(0.01));
    }

    @Test
    void shouldReturnZeroTpsWhenIntervalNonPositive() {
        // when
        var tps = BenchmarkLogReporter.computeTps(100L, 200L, 0L);

        // then
        assertThat(tps).isEqualTo(0.0);
    }

    @Test
    void shouldReturnZeroTpsWhenCounterRegressed() {
        // when
        var tps = BenchmarkLogReporter.computeTps(500L, 200L, 300L);

        // then
        assertThat(tps).isEqualTo(0.0);
    }

    @Test
    void shouldHandleZeroTotalForFailedPercent() {
        // given
        var snapshot = EMPTY_METRICS_SNAPSHOT;

        // when
        var line = BenchmarkLogReporter.formatLine(FIXED_INSTANT, 0.0, snapshot);

        // then
        assertThat(line).contains("     0%");
        assertThat(BenchmarkLogReporter.failedPercent(0L, 0L)).isEqualTo(0L);
    }

    @Test
    void shouldComputeFailedPercentAgainstTotal() {
        // when
        var percent = BenchmarkLogReporter.failedPercent(600L, 400L);

        // then
        assertThat(percent).isEqualTo(40L);
    }

    @Test
    void shouldWriteSessionHeaderAndLineOnFirstTick(@TempDir Path tempDir) throws Exception {
        // given
        var logPath = tempDir.resolve("benchmark.log");
        var snapshot = metricsSnapshotBuilder().build();
        given(metricsRecorder.snapshot()).willReturn(snapshot);
        var reporter = new BenchmarkLogReporter(metricsRecorder, logPath, 300L, Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC));

        // when
        reporter.tick();

        // then
        var expectedTps = (double) snapshot.processed() / 300.0;
        var expectedLine = BenchmarkLogReporter.formatLine(FIXED_INSTANT, expectedTps, snapshot);
        var lines = Files.readAllLines(logPath);
        assertThat(lines).containsExactly(BenchmarkLogReporter.HEADER, expectedLine);
    }

    @Test
    void shouldAppendLineWithoutRepeatingHeaderOnSubsequentTicks(@TempDir Path tempDir) throws Exception {
        // given
        var logPath = tempDir.resolve("benchmark.log");
        var firstSnapshot = metricsSnapshotBuilder()
                .received(800L)
                .written(480L)
                .failed(320L)
                .memos(3L)
                .transfers(60L)
                .accountsWritten(0L)
                .batches(15L)
                .slots(4L)
                .build();
        var secondSnapshot = metricsSnapshotBuilder().build();
        given(metricsRecorder.snapshot()).willReturn(firstSnapshot, secondSnapshot);
        var reporter = new BenchmarkLogReporter(metricsRecorder, logPath, 300L, Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC));

        // when
        reporter.tick();
        reporter.tick();

        // then
        var lines = Files.readAllLines(logPath);
        assertThat(lines).hasSize(3);
        assertThat(lines.get(0)).isEqualTo(BenchmarkLogReporter.HEADER);
        assertThat(lines.get(1)).contains("2026-04-06T19:54:15Z");
        assertThat(lines.get(2)).contains("2026-04-06T19:54:15Z");
    }

    @Test
    void shouldReflectDeltaBetweenTicksInTps(@TempDir Path tempDir) throws Exception {
        // given
        var logPath = tempDir.resolve("benchmark.log");
        var firstSnapshot = metricsSnapshotBuilder()
                .received(200L)
                .written(50L)
                .failed(50L)
                .memos(1L)
                .transfers(2L)
                .accountsWritten(0L)
                .batches(5L)
                .slots(1L)
                .build();
        var secondSnapshot = metricsSnapshotBuilder()
                .received(500L)
                .written(150L)
                .failed(150L)
                .memos(2L)
                .transfers(4L)
                .accountsWritten(0L)
                .batches(10L)
                .slots(2L)
                .build();
        given(metricsRecorder.snapshot()).willReturn(firstSnapshot, secondSnapshot);
        var reporter = new BenchmarkLogReporter(metricsRecorder, logPath, 300L, Clock.fixed(FIXED_INSTANT, ZoneOffset.UTC));

        // when
        reporter.tick();
        reporter.tick();

        // then
        var lines = Files.readAllLines(logPath);
        assertThat(lines).hasSize(3);
        assertThat(lines.get(1)).contains("   0.33");
        assertThat(lines.get(2)).contains("   0.67");
    }

    @Test
    void shouldRejectNullMetricsRecorder(@TempDir Path tempDir) {
        // when/then
        assertThatThrownBy(() -> new BenchmarkLogReporter(null, tempDir.resolve("benchmark.log"), 300L, Clock.systemUTC()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("metricsRecorder");
    }

    @Test
    void shouldRejectNullLogPath() {
        // when/then
        assertThatThrownBy(() -> new BenchmarkLogReporter(metricsRecorder, (Path) null, 300L, Clock.systemUTC()))
                .isInstanceOf(NullPointerException.class)
                .hasMessageContaining("logPath");
    }

    @Test
    void shouldRejectNonPositiveInterval(@TempDir Path tempDir) {
        // when/then
        assertThatThrownBy(() -> new BenchmarkLogReporter(metricsRecorder, tempDir.resolve("benchmark.log"), 0L, Clock.systemUTC()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("intervalSecs");
    }
}
