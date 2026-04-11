package com.stablebridge.prism.application;

import javax.sql.DataSource;

import com.stablebridge.prism.application.config.IndexerConfig;
import com.stablebridge.prism.domain.port.TransactionStream;

public final class TestIndexerApplication {

    private TestIndexerApplication() {}

    public static IndexerLifecycle start(
            IndexerConfig config, DataSource writePool, DataSource readPool, TransactionStream stream) {
        return IndexerApplication.start(config, writePool, readPool, stream);
    }
}
