package com.stablebridge.prism.testutil;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

public final class TestDataSourceFactory {

    private TestDataSourceFactory() {}

    public static HikariDataSource create(String jdbcUrl, boolean readOnly) {
        var config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername("indexer");
        config.setPassword("indexer");
        config.setMaximumPoolSize(5);
        config.setReadOnly(readOnly);
        return new HikariDataSource(config);
    }
}
