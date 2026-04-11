package com.stablebridge.prism.application.mapper;

import static com.stablebridge.prism.fixtures.StatsFixtures.SOME_INDEXER_STATS;
import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import com.stablebridge.prism.api.StatsResponse;

class StatsResponseMapperTest {

    private final StatsResponseMapper mapper = Mappers.getMapper(StatsResponseMapper.class);

    @Test
    void shouldMapIndexerStats() {
        // given
        var stats = SOME_INDEXER_STATS;

        // when
        var result = mapper.toResponse(stats);

        // then
        var expected = StatsResponse.builder()
                .totalTransactions(stats.totalTransactions())
                .totalFailed(stats.totalFailed())
                .totalTransfers(stats.totalTransfers())
                .totalMemos(stats.totalMemos())
                .totalAccounts(stats.totalAccounts())
                .build();
        assertThat(result).usingRecursiveComparison().isEqualTo(expected);
    }
}
