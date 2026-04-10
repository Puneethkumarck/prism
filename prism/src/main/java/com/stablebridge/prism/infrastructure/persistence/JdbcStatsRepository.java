package com.stablebridge.prism.infrastructure.persistence;

import java.sql.Connection;
import java.sql.SQLException;
import javax.sql.DataSource;

import com.stablebridge.prism.domain.model.IndexerStats;
import com.stablebridge.prism.domain.port.StatsRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class JdbcStatsRepository implements StatsRepository {

    private static final String COUNT_QUERY =
            "SELECT COALESCE(n_live_tup, 0) FROM pg_stat_user_tables WHERE relname = ?";

    private final DataSource readPool;

    @Override
    public IndexerStats getStats() {
        try (var conn = readPool.getConnection()) {
            return IndexerStats.builder()
                    .totalTransactions(queryTableCount(conn, "transactions"))
                    .totalFailed(queryTableCount(conn, "failed_transactions"))
                    .totalTransfers(queryTableCount(conn, "large_transfers"))
                    .totalMemos(queryTableCount(conn, "memos"))
                    .totalAccounts(queryTableCount(conn, "accounts"))
                    .build();
        } catch (SQLException e) {
            throw new RuntimeException("Failed to query indexer stats", e);
        }
    }

    private long queryTableCount(Connection conn, String tableName) throws SQLException {
        try (var ps = conn.prepareStatement(COUNT_QUERY)) {
            ps.setString(1, tableName);
            try (var rs = ps.executeQuery()) {
                return rs.next() ? rs.getLong(1) : 0L;
            }
        }
    }
}
