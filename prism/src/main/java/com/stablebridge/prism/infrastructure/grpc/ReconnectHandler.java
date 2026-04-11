package com.stablebridge.prism.infrastructure.grpc;

import java.util.concurrent.locks.ReentrantLock;

public class ReconnectHandler {

    public static final long RECONNECT_BASE_SECS = 2L;
    public static final long RECONNECT_MAX_SECS = 30L;
    public static final long RECONNECT_RESET_SECS = 60L;

    private static final int MAX_ATTEMPT_EXPONENT = 4;

    private final ReentrantLock lock = new ReentrantLock();
    private long attempt;

    public long nextDelay() {
        lock.lock();
        try {
            attempt++;
            var exponent = (int) Math.min(attempt, MAX_ATTEMPT_EXPONENT);
            var delay = RECONNECT_BASE_SECS * (1L << exponent);
            return Math.min(delay, RECONNECT_MAX_SECS);
        } finally {
            lock.unlock();
        }
    }

    public void resetIfStable(long connectedDurationSecs) {
        lock.lock();
        try {
            if (connectedDurationSecs >= RECONNECT_RESET_SECS) {
                attempt = 0;
            }
        } finally {
            lock.unlock();
        }
    }
}
