package com.stablebridge.prism.domain.model;

import lombok.Builder;

@Builder(toBuilder = true)
public record BatchResult(
        long written,
        long failed,
        long memos,
        long transfers
) {}
