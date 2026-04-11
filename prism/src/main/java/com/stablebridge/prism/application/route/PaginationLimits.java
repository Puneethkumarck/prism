package com.stablebridge.prism.application.route;

final class PaginationLimits {

    static final long DEFAULT_LIMIT = 50L;
    static final long DEFAULT_OFFSET = 0L;
    static final long MIN_LIMIT = 1L;
    static final long MAX_LIMIT = 500L;

    private PaginationLimits() {}

    static long clampLimit(long limit) {
        return Math.max(MIN_LIMIT, Math.min(limit, MAX_LIMIT));
    }
}
