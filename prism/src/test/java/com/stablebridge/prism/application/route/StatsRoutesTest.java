package com.stablebridge.prism.application.route;

import static com.stablebridge.prism.fixtures.StatsFixtures.SOME_INDEXER_STATS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stablebridge.prism.api.StatsResponse;
import com.stablebridge.prism.application.mapper.StatsResponseMapper;
import com.stablebridge.prism.domain.port.StatsRepository;

@ExtendWith(MockitoExtension.class)
class StatsRoutesTest {

    @Mock
    private StatsRepository statsRepository;

    @Spy
    private final StatsResponseMapper mapper = Mappers.getMapper(StatsResponseMapper.class);

    @InjectMocks
    private StatsRoutes routes;

    @Test
    void shouldReturnMappedStats() {
        // given
        given(statsRepository.getStats()).willReturn(SOME_INDEXER_STATS);

        // when
        var result = routes.currentStats();

        // then
        var expected = StatsResponse.builder()
                .totalTransactions(SOME_INDEXER_STATS.totalTransactions())
                .totalFailed(SOME_INDEXER_STATS.totalFailed())
                .totalTransfers(SOME_INDEXER_STATS.totalTransfers())
                .totalMemos(SOME_INDEXER_STATS.totalMemos())
                .totalAccounts(SOME_INDEXER_STATS.totalAccounts())
                .build();
        assertThat(result).usingRecursiveComparison().isEqualTo(expected);
    }
}
