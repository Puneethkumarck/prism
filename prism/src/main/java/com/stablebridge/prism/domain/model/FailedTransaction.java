package com.stablebridge.prism.domain.model;

import lombok.Builder;

@Builder(toBuilder = true)
public record FailedTransaction(
        String signature,
        long slot,
        String error
) {}
