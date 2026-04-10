package com.stablebridge.prism.api;

import lombok.Builder;

@Builder(toBuilder = true)
public record HealthResponse(
        String status,
        long uptimeSecs
) {}
