package com.stablebridge.prism.infrastructure.persistence;

import java.sql.SQLException;
import java.util.List;
import javax.sql.DataSource;

import com.stablebridge.prism.domain.model.FailedTransaction;
import com.stablebridge.prism.domain.port.FailedTransactionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class JdbcFailedTransactionRepository implements FailedTransactionRepository {

    private static final String INSERT_SQL =
            "INSERT INTO failed_transactions (signature, slot, error) VALUES (?, ?, ?)";

    private final DataSource writePool;

    @Override
    public void bulkInsert(List<FailedTransaction> batch) {
        if (batch.isEmpty()) {
            return;
        }
        try (var conn = writePool.getConnection();
                var ps = conn.prepareStatement(INSERT_SQL)) {
            for (var ft : batch) {
                ps.setString(1, ft.signature().value());
                ps.setLong(2, ft.slot());
                ps.setString(3, ft.error());
                ps.addBatch();
            }
            ps.executeBatch();
            log.debug("Batch inserted {} failed transactions", batch.size());
        } catch (SQLException e) {
            throw new RuntimeException("Failed to bulk insert failed transactions", e);
        }
    }
}
