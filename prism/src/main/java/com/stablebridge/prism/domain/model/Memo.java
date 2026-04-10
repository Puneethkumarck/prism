package com.stablebridge.prism.domain.model;

import lombok.Builder;

@Builder(toBuilder = true)
public record Memo(
        String signature,
        String memoText
) {}
