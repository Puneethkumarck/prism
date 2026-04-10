package com.stablebridge.prism.infrastructure.persistence;

import java.math.BigDecimal;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

import com.stablebridge.prism.domain.model.LargeTransfer;
import com.stablebridge.prism.domain.model.Signature;
import com.stablebridge.prism.domain.port.TransferRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class JdbcTransferRepository implements TransferRepository {

    private static final String INSERT_SQL =
            "INSERT INTO large_transfers (signature, slot, amount) VALUES (?, ?, ?)";

    private static final String FIND_BY_MIN_AMOUNT_SQL =
            "SELECT signature, slot, amount FROM large_transfers WHERE amount >= ? ORDER BY amount DESC, signature ASC LIMIT ? OFFSET ?";

    private static final String COUNT_BY_MIN_AMOUNT_SQL =
            "SELECT COUNT(*) FROM large_transfers WHERE amount >= ?";

    private final DataSource writePool;
    private final DataSource readPool;

    @Override
    public void bulkInsert(List<LargeTransfer> transfers) {
        if (transfers.isEmpty()) {
            return;
        }
        try (var conn = writePool.getConnection()) {
            conn.setAutoCommit(false);
            try (var ps = conn.prepareStatement(INSERT_SQL)) {
                for (var transfer : transfers) {
                    ps.setString(1, transfer.signature().value());
                    ps.setLong(2, transfer.slot());
                    ps.setBigDecimal(3, transfer.amount());
                    ps.addBatch();
                }
                ps.executeBatch();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
            log.debug("Batch inserted {} transfers", transfers.size());
        } catch (SQLException e) {
            throw new RuntimeException("Failed to bulk insert transfers", e);
        }
    }

    @Override
    public List<LargeTransfer> findByMinAmount(BigDecimal minAmount, long limit, long offset) {
        try (var conn = readPool.getConnection();
                var ps = conn.prepareStatement(FIND_BY_MIN_AMOUNT_SQL)) {
            ps.setBigDecimal(1, minAmount);
            ps.setLong(2, limit);
            ps.setLong(3, offset);
            try (var rs = ps.executeQuery()) {
                var results = new ArrayList<LargeTransfer>();
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
                return results;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find transfers by min amount", e);
        }
    }

    @Override
    public long countByMinAmount(BigDecimal minAmount) {
        try (var conn = readPool.getConnection();
                var ps = conn.prepareStatement(COUNT_BY_MIN_AMOUNT_SQL)) {
            ps.setBigDecimal(1, minAmount);
            try (var rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to count transfers by min amount", e);
        }
    }

    private LargeTransfer mapRow(ResultSet rs) throws SQLException {
        return LargeTransfer.builder()
                .signature(new Signature(rs.getString("signature")))
                .slot(rs.getLong("slot"))
                .amount(rs.getBigDecimal("amount"))
                .build();
    }
}
