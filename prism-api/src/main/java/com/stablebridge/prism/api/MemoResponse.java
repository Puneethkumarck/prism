package com.stablebridge.prism.api;

import java.time.Instant;

import lombok.Builder;

@Builder(toBuilder = true)
public record MemoResponse(
        int id,
        String signature,
        String memo,
        Instant createdAt
) {}
