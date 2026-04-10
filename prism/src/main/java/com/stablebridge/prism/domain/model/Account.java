package com.stablebridge.prism.domain.model;

import lombok.Builder;

@Builder(toBuilder = true)
public record Account(
        String pubkey,
        long lamports,
        long slot,
        boolean executable,
        long rentEpoch
) {}
