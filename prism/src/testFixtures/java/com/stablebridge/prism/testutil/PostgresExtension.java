package com.stablebridge.prism.testutil;

import javax.sql.DataSource;

import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.testcontainers.containers.PostgreSQLContainer;

public class PostgresExtension implements BeforeAllCallback {

    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine")
                    .withDatabaseName("indexer")
                    .withUsername("indexer")
                    .withPassword("indexer");

    private static volatile DataSource writePool;
    private static volatile DataSource readPool;

    @Override
    public void beforeAll(ExtensionContext context) {
        if (!POSTGRES.isRunning()) {
            POSTGRES.start();
            writePool = TestDataSourceFactory.create(POSTGRES.getJdbcUrl(), false);
            readPool = TestDataSourceFactory.create(POSTGRES.getJdbcUrl(), true);
        }
    }

    public static PostgreSQLContainer<?> container() {
        return POSTGRES;
    }

    public static DataSource writePool() {
        return writePool;
    }

    public static DataSource readPool() {
        return readPool;
    }
}
