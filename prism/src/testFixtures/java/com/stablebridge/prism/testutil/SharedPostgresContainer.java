package com.stablebridge.prism.testutil;

import javax.sql.DataSource;

import org.testcontainers.containers.PostgreSQLContainer;

import com.stablebridge.prism.infrastructure.persistence.FlywayMigrator;

public final class SharedPostgresContainer {

    private static final PostgreSQLContainer<?> POSTGRES =
            new PostgreSQLContainer<>("postgres:16-alpine");

    private static DataSource writePool;
    private static DataSource readPool;

    static {
        POSTGRES.start();
        writePool = TestDataSourceFactory.create(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword(), false);
        readPool = TestDataSourceFactory.create(
                POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword(), true);
        FlywayMigrator.migrate(writePool);
    }

    private SharedPostgresContainer() {}

    public static DataSource writePool() {
        return writePool;
    }

    public static DataSource readPool() {
        return readPool;
    }
}
