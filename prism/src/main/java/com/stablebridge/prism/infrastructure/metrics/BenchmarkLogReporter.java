package com.stablebridge.prism.infrastructure.metrics;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.Clock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.Objects;
import java.util.concurrent.locks.ReentrantLock;

import com.stablebridge.prism.domain.model.MetricsSnapshot;
import com.stablebridge.prism.domain.port.MetricsRecorder;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BenchmarkLogReporter {

    static final String HEADER = String.format(
            Locale.ROOT,
            "%-20s | %7s | %9s | %9s | %9s | %7s | %5s | %5s | %9s | %7s | %5s",
            "timestamp",
            "tps",
            "recv",
            "written",
            "failed",
            "failed%",
            "memos",
            "xfers",
            "accts",
            "batches",
            "slots");

    static final String LINE_FORMAT =
            "%-20s | %7.2f | %9d | %9d | %9d | %6d%% | %5d | %5d | %9d | %7d | %5d";

    private final MetricsRecorder metricsRecorder;
    private final Path logPath;
    private final long intervalSecs;
    private final Clock clock;
    private final ReentrantLock lock = new ReentrantLock();

    private volatile Thread worker;
    private long previousProcessed;
    private boolean headerWritten;

    public BenchmarkLogReporter(MetricsRecorder metricsRecorder, String logPath, long intervalSecs) {
        this(metricsRecorder, Path.of(Objects.requireNonNull(logPath, "logPath must not be null")), intervalSecs, Clock.systemUTC());
    }

    BenchmarkLogReporter(MetricsRecorder metricsRecorder, Path logPath, long intervalSecs, Clock clock) {
        this.metricsRecorder = Objects.requireNonNull(metricsRecorder, "metricsRecorder must not be null");
        this.logPath = Objects.requireNonNull(logPath, "logPath must not be null");
        this.clock = Objects.requireNonNull(clock, "clock must not be null");
        if (intervalSecs <= 0) {
            throw new IllegalArgumentException("intervalSecs must be positive");
        }
        this.intervalSecs = intervalSecs;
    }

    public void run() {
        worker = Thread.currentThread();
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(intervalSecs * 1000L);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return;
            }
            try {
                tick();
            } catch (RuntimeException e) {
                log.warn("benchmark log tick failed", e);
            }
        }
    }

    public void close() {
        var t = worker;
        if (t != null) {
            t.interrupt();
        }
    }

    void tick() {
        lock.lock();
        try {
            var snapshot = metricsRecorder.snapshot();
            var currProcessed = snapshot.processed();
            var tps = computeTps(previousProcessed, currProcessed, intervalSecs);
            var timestamp = clock.instant().truncatedTo(ChronoUnit.SECONDS);
            var line = formatLine(timestamp, tps, snapshot);
            if (append(line)) {
                previousProcessed = currProcessed;
            }
        } finally {
            lock.unlock();
        }
    }

    private boolean append(String line) {
        try {
            var parent = logPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            var buffer = new StringBuilder();
            var needsHeader = !headerWritten;
            if (needsHeader) {
                buffer.append(HEADER).append(System.lineSeparator());
            }
            buffer.append(line).append(System.lineSeparator());
            Files.writeString(logPath, buffer.toString(), StandardOpenOption.CREATE, StandardOpenOption.APPEND);
            if (needsHeader) {
                headerWritten = true;
            }
            return true;
        } catch (IOException e) {
            log.warn("failed to append benchmark log line to {}", logPath, e);
            return false;
        }
    }

    static double computeTps(long previousProcessed, long currentProcessed, long intervalSecs) {
        if (intervalSecs <= 0 || currentProcessed < previousProcessed) {
            return 0.0;
        }
        return (double) (currentProcessed - previousProcessed) / (double) intervalSecs;
    }

    static long failedPercent(long written, long failed) {
        var total = written + failed;
        if (total == 0) {
            return 0L;
        }
        return (failed * 100L) / total;
    }

    static String formatLine(Instant timestamp, double tps, MetricsSnapshot snapshot) {
        return String.format(
                Locale.ROOT,
                LINE_FORMAT,
                timestamp.toString(),
                tps,
                snapshot.received(),
                snapshot.written(),
                snapshot.failed(),
                failedPercent(snapshot.written(), snapshot.failed()),
                snapshot.memos(),
                snapshot.transfers(),
                snapshot.accountsWritten(),
                snapshot.batches(),
                snapshot.slots());
    }
}
