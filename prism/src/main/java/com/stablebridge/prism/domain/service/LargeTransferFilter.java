package com.stablebridge.prism.domain.service;

import java.math.BigDecimal;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class LargeTransferFilter {

    public static final BigDecimal LARGE_TRANSFER_THRESHOLD_SOL = new BigDecimal("1.0");

    private LargeTransferFilter() {}

    public static boolean isLargeTransfer(BigDecimal amount) {
        return amount.compareTo(LARGE_TRANSFER_THRESHOLD_SOL) > 0;
    }
}
