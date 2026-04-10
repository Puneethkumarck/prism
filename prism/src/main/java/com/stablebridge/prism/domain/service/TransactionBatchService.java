package com.stablebridge.prism.domain.service;

import java.util.ArrayList;
import java.util.List;
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
        this.processor = processor;
    }

    public void enqueue(SolanaTransaction tx) {
        queue.add(tx);
    }

    public void run() {
        var buffer = new ArrayList<SolanaTransaction>();
        while (running || !queue.isEmpty()) {
            try {
                var tx = queue.poll(FLUSH_INTERVAL_MS, TimeUnit.MILLISECONDS);
                if (tx != null) {
                    buffer.add(tx);
                }
                if (buffer.size() >= BATCH_SIZE || (tx == null && !buffer.isEmpty())) {
                    processor.process(List.copyOf(buffer));
                    buffer.clear();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (!buffer.isEmpty()) {
            processor.process(List.copyOf(buffer));
        }
    }

    public void close() {
        running = false;
    }
}
