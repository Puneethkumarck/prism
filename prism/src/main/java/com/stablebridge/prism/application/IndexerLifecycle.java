package com.stablebridge.prism.application;

import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.ReentrantLock;

import com.stablebridge.prism.domain.port.TransactionStream;
import com.stablebridge.prism.domain.service.AccountBatchService;
import com.stablebridge.prism.domain.service.TransactionBatchService;
import com.stablebridge.prism.infrastructure.metrics.BenchmarkLogReporter;

import io.helidon.webserver.WebServer;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class IndexerLifecycle {

    static final long SHUTDOWN_TIMEOUT_SECS = 10L;

    private final WebServer server;
    private final ExecutorService executor;
    private final TransactionStream stream;
    private final TransactionBatchService txBatchService;
    private final AccountBatchService acctBatchService;
    private final BenchmarkLogReporter benchmarkReporter;
    private final ReentrantLock stopLock = new ReentrantLock();
    private volatile boolean stopped;

    IndexerLifecycle(
            WebServer server,
            ExecutorService executor,
            TransactionStream stream,
            TransactionBatchService txBatchService,
            AccountBatchService acctBatchService,
            BenchmarkLogReporter benchmarkReporter) {
        this.server = Objects.requireNonNull(server, "server must not be null");
        this.executor = Objects.requireNonNull(executor, "executor must not be null");
        this.stream = Objects.requireNonNull(stream, "stream must not be null");
        this.txBatchService = Objects.requireNonNull(txBatchService, "txBatchService must not be null");
        this.acctBatchService = Objects.requireNonNull(acctBatchService, "acctBatchService must not be null");
        this.benchmarkReporter = Objects.requireNonNull(benchmarkReporter, "benchmarkReporter must not be null");
    }

    public int port() {
        return server.port();
    }

    public boolean isRunning() {
        return !stopped && server.isRunning();
    }

    public void stop() {
        stopLock.lock();
        try {
            if (stopped) {
                return;
            }
            stopped = true;
            log.info("Stopping Prism indexer");
            closeSafely("stream", stream::close);
            closeSafely("transaction batch service", txBatchService::close);
            closeSafely("account batch service", acctBatchService::close);
            closeSafely("benchmark reporter", benchmarkReporter::close);
            try {
                executor.shutdown();
                if (!executor.awaitTermination(SHUTDOWN_TIMEOUT_SECS, TimeUnit.SECONDS)) {
                    log.warn(
                            "Virtual thread executor did not terminate in {}s; forcing shutdown",
                            SHUTDOWN_TIMEOUT_SECS);
                    executor.shutdownNow();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                executor.shutdownNow();
            } catch (RuntimeException e) {
                log.warn("Failed to shut down virtual thread executor", e);
            }
            closeSafely("web server", server::stop);
            log.info("Goodbye!");
        } finally {
            stopLock.unlock();
        }
    }

    private static void closeSafely(String component, Runnable action) {
        try {
            action.run();
        } catch (RuntimeException e) {
            log.warn("Failed to close {}", component, e);
        }
    }
}
