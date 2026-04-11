package com.stablebridge.prism.application.mapper;

import org.mapstruct.Mapper;

import com.stablebridge.prism.api.StatsResponse;
import com.stablebridge.prism.domain.model.IndexerStats;

@Mapper(componentModel = "jsr330")
public interface StatsResponseMapper {

    StatsResponse toResponse(IndexerStats stats);
}
