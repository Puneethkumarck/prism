package com.stablebridge.prism.api;

import lombok.Builder;

@Builder(toBuilder = true)
public record ErrorResponse(
        String error,
        int status
) {}
