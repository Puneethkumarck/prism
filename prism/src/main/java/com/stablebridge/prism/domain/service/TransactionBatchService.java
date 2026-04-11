package com.stablebridge.prism.domain.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.LinkedTransferQueue;
import java.util.concurrent.TimeUnit;

import com.stablebridge.prism.domain.model.SolanaTransaction;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class TransactionBatchService {

    static final int BATCH_SIZE = 200;
    static final long FLUSH_INTERVAL_MS = 100;

    private final TransactionProcessor processor;
    private final LinkedTransferQueue<SolanaTransaction> queue = new LinkedTransferQueue<>();
    private volatile boolean running = true;

    public TransactionBatchService(TransactionProcessor processor) {
        this.processor = Objects.requireNonNull(processor, "processor must not be null");
    }

    public void enqueue(SolanaTransaction tx) {
        Objects.requireNonNull(tx, "tx must not be null");
        queue.add(tx);
    }

    public void run() {
        log.info("TransactionBatchService started");
        var buffer = new ArrayList<SolanaTransaction>();
        while (running || !queue.isEmpty()) {
            try {
                var tx = queue.poll(FLUSH_INTERVAL_MS, TimeUnit.MILLISECONDS);
                if (tx != null) {
                    buffer.add(tx);
                }
                if (buffer.size() >= BATCH_SIZE || (tx == null && !buffer.isEmpty())) {
                    flush(buffer);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        flush(buffer);
        log.info("TransactionBatchService stopped");
    }

    private void flush(List<SolanaTransaction> buffer) {
        if (buffer.isEmpty()) {
            return;
        }
        var size = buffer.size();
        try {
            log.debug("Flushing batch of {} transactions", size);
            processor.process(List.copyOf(buffer));
        } catch (Exception e) {
            log.error("Failed to flush batch of {} transactions; dropping and continuing", size, e);
        } finally {
            buffer.clear();
        }
    }

    public void close() {
        log.info("TransactionBatchService closing");
        running = false;
    }
}
