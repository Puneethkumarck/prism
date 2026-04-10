package com.stablebridge.prism.fixtures;

import com.stablebridge.prism.domain.model.BatchResult;
import com.stablebridge.prism.domain.model.IndexerStats;

public final class StatsFixtures {

    private StatsFixtures() {}

    public static BatchResult.BatchResultBuilder batchResultBuilder() {
        return BatchResult.builder()
                .written(10)
                .failed(2)
                .memos(3)
                .transfers(1);
    }

    public static final BatchResult SOME_BATCH_RESULT = batchResultBuilder().build();

    public static IndexerStats.IndexerStatsBuilder indexerStatsBuilder() {
        return IndexerStats.builder()
                .totalTransactions(1000)
                .totalFailed(50)
                .totalTransfers(200)
                .totalMemos(100)
                .totalAccounts(500);
    }

    public static final IndexerStats SOME_INDEXER_STATS = indexerStatsBuilder().build();
}
