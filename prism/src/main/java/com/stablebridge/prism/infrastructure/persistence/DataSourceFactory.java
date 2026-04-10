package com.stablebridge.prism.infrastructure.persistence;

import java.util.Objects;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class DataSourceFactory {

    private static final int MAX_POOL_SIZE = 20;
    private static final int MIN_IDLE = 5;
    private static final long CONNECTION_TIMEOUT = 10_000;
    private static final long IDLE_TIMEOUT = 60_000;

    public static HikariDataSource createWritePool(String jdbcUrl) {
        Objects.requireNonNull(jdbcUrl, "jdbcUrl must not be null");
        var config = new HikariConfig();
        config.setJdbcUrl(appendReWriteBatchedInserts(jdbcUrl));
        config.setMaximumPoolSize(MAX_POOL_SIZE);
        config.setMinimumIdle(MIN_IDLE);
        config.setConnectionTimeout(CONNECTION_TIMEOUT);
        config.setIdleTimeout(IDLE_TIMEOUT);
        config.setPoolName("write-pool");
        return new HikariDataSource(config);
    }

    public static HikariDataSource createReadPool(String jdbcUrl) {
        Objects.requireNonNull(jdbcUrl, "jdbcUrl must not be null");
        var config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setMaximumPoolSize(MAX_POOL_SIZE);
        config.setMinimumIdle(MIN_IDLE);
        config.setConnectionTimeout(CONNECTION_TIMEOUT);
        config.setIdleTimeout(IDLE_TIMEOUT);
        config.setPoolName("read-pool");
        config.setReadOnly(true);
        return new HikariDataSource(config);
    }

    private static String appendReWriteBatchedInserts(String jdbcUrl) {
        var separator = jdbcUrl.contains("?") ? "&" : "?";
        return jdbcUrl + separator + "reWriteBatchedInserts=true";
    }
}
