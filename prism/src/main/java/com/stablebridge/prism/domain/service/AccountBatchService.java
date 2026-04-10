package com.stablebridge.prism.domain.service;

import static java.util.concurrent.TimeUnit.MILLISECONDS;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ArrayBlockingQueue;

import com.stablebridge.prism.domain.model.Account;
import com.stablebridge.prism.domain.model.Pubkey;
import com.stablebridge.prism.domain.port.AccountRepository;
import com.stablebridge.prism.domain.port.MetricsRecorder;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public class AccountBatchService {

    static final int BATCH_SIZE = 200;
    static final long FLUSH_INTERVAL_MS = 2000;
    static final int QUEUE_CAPACITY = 10_000;

    private final AccountRepository accountRepository;
    private final MetricsRecorder metricsRecorder;
    private final ArrayBlockingQueue<Account> queue = new ArrayBlockingQueue<>(QUEUE_CAPACITY);
    private volatile boolean running = true;

    public AccountBatchService(AccountRepository accountRepository, MetricsRecorder metricsRecorder) {
        this.accountRepository = Objects.requireNonNull(accountRepository, "accountRepository must not be null");
        this.metricsRecorder = Objects.requireNonNull(metricsRecorder, "metricsRecorder must not be null");
    }

    public boolean offer(Account account) {
        return queue.offer(account);
    }

    public void run() {
        var buffer = new ArrayList<Account>();
        while (running || !queue.isEmpty()) {
            try {
                var account = queue.poll(FLUSH_INTERVAL_MS, MILLISECONDS);
                if (account != null) {
                    buffer.add(account);
                }
                if (buffer.size() >= BATCH_SIZE || (account == null && !buffer.isEmpty())) {
                    try {
                        flush(buffer);
                    } catch (Exception e) {
                        log.error("Failed to flush {} accounts", buffer.size(), e);
                    }
                    buffer.clear();
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            }
        }
        if (!buffer.isEmpty()) {
            try {
                flush(buffer);
            } catch (Exception e) {
                log.error("Failed to flush {} remaining accounts on shutdown", buffer.size(), e);
            }
        }
    }

    private void flush(List<Account> buffer) {
        var map = new HashMap<Pubkey, Account>();
        buffer.forEach(a -> map.merge(a.pubkey(), a, (prev, curr) -> curr.slot() > prev.slot() ? curr : prev));
        var deduped = List.copyOf(map.values());
        accountRepository.batchUpsert(deduped);
        metricsRecorder.recordAccountsWritten(deduped.size());
        log.debug("Flushed {} accounts", deduped.size());
    }

    public void close() {
        running = false;
    }
}
