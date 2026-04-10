package com.stablebridge.prism.domain.service;

import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class LargeTransferFilter {

    public static final double LARGE_TRANSFER_THRESHOLD_SOL = 1.0;

    private LargeTransferFilter() {}

    public static boolean isLargeTransfer(double amount) {
        return amount > LARGE_TRANSFER_THRESHOLD_SOL;
    }
}
