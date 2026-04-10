package com.stablebridge.prism.infrastructure.persistence;

import static org.assertj.core.api.Assertions.assertThat;

import java.sql.SQLException;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;

import com.zaxxer.hikari.HikariDataSource;

class DataSourceFactoryIntegrationTest {

    static PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:16-alpine");
    static HikariDataSource writePool;
    static HikariDataSource readPool;

    @BeforeAll
    static void setUp() {
        postgres.start();
        writePool = DataSourceFactory.createWritePool(postgres.getJdbcUrl()
                + "&user=" + postgres.getUsername()
                + "&password=" + postgres.getPassword());
        readPool = DataSourceFactory.createReadPool(postgres.getJdbcUrl()
                + "&user=" + postgres.getUsername()
                + "&password=" + postgres.getPassword());
    }

    @AfterAll
    static void tearDown() {
        if (writePool != null) writePool.close();
        if (readPool != null) readPool.close();
        postgres.stop();
    }

    @Test
    void shouldCreateWritePoolAndConnect() throws SQLException {
        // given
        // when
        try (var conn = writePool.getConnection();
                var stmt = conn.createStatement();
                var rs = stmt.executeQuery("SELECT 1")) {

            // then
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isEqualTo(1);
        }
    }

    @Test
    void shouldCreateReadPoolAndConnect() throws SQLException {
        // given
        // when
        try (var conn = readPool.getConnection();
                var stmt = conn.createStatement();
                var rs = stmt.executeQuery("SELECT 1")) {

            // then
            assertThat(rs.next()).isTrue();
            assertThat(rs.getInt(1)).isEqualTo(1);
        }
    }

    @Test
    void shouldRunFlywayMigrations() throws SQLException {
        // given
        FlywayMigrator.migrate(writePool);

        // when
        try (var conn = writePool.getConnection();
                var ps = conn.prepareStatement(
                        "SELECT table_name FROM information_schema.tables WHERE table_schema = 'public' ORDER BY table_name")) {
            try (var rs = ps.executeQuery()) {

                // then
                var tables = new java.util.ArrayList<String>();
                while (rs.next()) {
                    tables.add(rs.getString("table_name"));
                }
                assertThat(tables).contains("transactions", "accounts", "failed_transactions", "large_transfers", "memos");
            }
        }
    }

    @Test
    void shouldHaveReadOnlyEnabledOnReadPool() throws SQLException {
        // given
        // when
        try (var conn = readPool.getConnection()) {

            // then
            assertThat(conn.isReadOnly()).isTrue();
        }
    }

    @Test
    void shouldHaveReWriteBatchedInsertsEnabled() throws SQLException {
        // given
        // when
        try (var conn = writePool.getConnection()) {

            // then
            var url = conn.getMetaData().getURL();
            assertThat(url).contains("reWriteBatchedInserts=true");
        }
    }
}
