package com.stablebridge.prism.domain.port;

import com.stablebridge.prism.domain.model.IndexerStats;

public interface StatsRepository {

    IndexerStats getStats();
}
