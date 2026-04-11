package com.stablebridge.prism.api;

import com.fasterxml.jackson.databind.PropertyNamingStrategies;
import com.fasterxml.jackson.databind.annotation.JsonNaming;

import lombok.Builder;

@JsonNaming(PropertyNamingStrategies.SnakeCaseStrategy.class)
@Builder(toBuilder = true)
public record HealthResponse(
        String status,
        long uptimeSecs
) {
    public HealthResponse {
        if (uptimeSecs < 0) {
            throw new IllegalArgumentException("uptimeSecs must not be negative");
        }
    }
}
