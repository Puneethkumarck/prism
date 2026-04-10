package com.stablebridge.prism.api;

import java.util.List;

import lombok.Builder;

@Builder(toBuilder = true)
public record Page<T>(
        List<T> data,
        long total,
        long limit,
        long offset
) {}
