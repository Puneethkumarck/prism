package com.stablebridge.prism.infrastructure.persistence;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;

import org.postgresql.PGConnection;

import com.stablebridge.prism.domain.exception.PersistenceException;
import com.stablebridge.prism.domain.model.Signature;
import com.stablebridge.prism.domain.model.SolanaTransaction;
import com.stablebridge.prism.domain.port.TransactionRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class CopyTransactionRepository implements TransactionRepository {

    private static final String COPY_SQL =
            "COPY staging_transactions (signature, slot, success) FROM STDIN (FORMAT TEXT)";

    private static final String MERGE_SQL =
            "INSERT INTO transactions (signature, slot, success) SELECT signature, slot, success FROM staging_transactions ON CONFLICT (signature) DO NOTHING";

    private static final String TRUNCATE_STAGING_SQL = "TRUNCATE staging_transactions";

    private static final String LOCK_STAGING_SQL =
            "LOCK TABLE staging_transactions IN EXCLUSIVE MODE";

    private static final String FIND_BY_SIGNATURE_SQL =
            "SELECT signature, slot, success, created_at FROM transactions WHERE signature = ?";

    private static final String FIND_BY_SLOT_SQL =
            "SELECT signature, slot, success, created_at FROM transactions WHERE slot = ? ORDER BY created_at ASC, signature ASC";

    private static final String FIND_ALL_SQL =
            "SELECT signature, slot, success, created_at FROM transactions ORDER BY created_at DESC, signature DESC LIMIT ? OFFSET ?";

    private static final String FIND_ALL_BY_SUCCESS_SQL =
            "SELECT signature, slot, success, created_at FROM transactions WHERE success = ? ORDER BY created_at DESC, signature DESC LIMIT ? OFFSET ?";

    private static final String COUNT_ALL_SQL = "SELECT COUNT(*) FROM transactions";

    private static final String COUNT_BY_SUCCESS_SQL =
            "SELECT COUNT(*) FROM transactions WHERE success = ?";

    private final DataSource writePool;
    private final DataSource readPool;

    @Override
    public void bulkInsert(List<SolanaTransaction> batch) {
        var successful = batch.stream().filter(tx -> !tx.failed()).toList();
        if (successful.isEmpty()) {
            return;
        }
        try (var conn = writePool.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (var stmt = conn.createStatement()) {
                    stmt.execute(LOCK_STAGING_SQL);
                }
                var pgConn = conn.unwrap(PGConnection.class);
                var tsv = buildTsv(successful);
                pgConn.getCopyAPI()
                        .copyIn(COPY_SQL, new ByteArrayInputStream(tsv.getBytes(StandardCharsets.UTF_8)));
                try (var stmt = conn.createStatement()) {
                    stmt.execute(MERGE_SQL);
                    stmt.execute(TRUNCATE_STAGING_SQL);
                }
                conn.commit();
            } catch (SQLException | IOException e) {
                conn.rollback();
                throw e;
            }
            log.debug("COPY inserted {} transactions", successful.size());
        } catch (SQLException | IOException e) {
            throw new PersistenceException("Failed to COPY insert transactions", e);
        }
    }

    @Override
    public Optional<SolanaTransaction> findBySignature(Signature signature) {
        try (var conn = readPool.getConnection();
                var ps = conn.prepareStatement(FIND_BY_SIGNATURE_SQL)) {
            ps.setString(1, signature.value());
            try (var rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new PersistenceException("Failed to find transaction by signature", e);
        }
    }

    @Override
    public List<SolanaTransaction> findBySlot(long slot) {
        try (var conn = readPool.getConnection();
                var ps = conn.prepareStatement(FIND_BY_SLOT_SQL)) {
            ps.setLong(1, slot);
            try (var rs = ps.executeQuery()) {
                var results = new ArrayList<SolanaTransaction>();
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
                return results;
            }
        } catch (SQLException e) {
            throw new PersistenceException("Failed to find transactions by slot", e);
        }
    }

    @Override
    public List<SolanaTransaction> findAll(long limit, long offset, Boolean success) {
        if (limit < 0) {
            throw new IllegalArgumentException("limit must be non-negative");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be non-negative");
        }
        var sql = success != null ? FIND_ALL_BY_SUCCESS_SQL : FIND_ALL_SQL;
        try (var conn = readPool.getConnection();
                var ps = conn.prepareStatement(sql)) {
            if (success != null) {
                ps.setBoolean(1, success);
                ps.setLong(2, limit);
                ps.setLong(3, offset);
            } else {
                ps.setLong(1, limit);
                ps.setLong(2, offset);
            }
            try (var rs = ps.executeQuery()) {
                var results = new ArrayList<SolanaTransaction>();
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
                return results;
            }
        } catch (SQLException e) {
            throw new PersistenceException("Failed to find transactions", e);
        }
    }

    @Override
    public long countAll() {
        try (var conn = readPool.getConnection();
                var ps = conn.prepareStatement(COUNT_ALL_SQL);
                var rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        } catch (SQLException e) {
            throw new PersistenceException("Failed to count transactions", e);
        }
    }

    @Override
    public long countBySuccess(boolean success) {
        try (var conn = readPool.getConnection();
                var ps = conn.prepareStatement(COUNT_BY_SUCCESS_SQL)) {
            ps.setBoolean(1, success);
            try (var rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1);
            }
        } catch (SQLException e) {
            throw new PersistenceException("Failed to count transactions by success", e);
        }
    }

    private String buildTsv(List<SolanaTransaction> transactions) {
        var sb = new StringBuilder();
        for (var tx : transactions) {
            sb.append(escapeCopyText(tx.signature().value()))
                    .append('\t')
                    .append(tx.slot())
                    .append('\t')
                    .append('t')
                    .append('\n');
        }
        return sb.toString();
    }

    private String escapeCopyText(String value) {
        return value.replace("\\", "\\\\")
                .replace("\t", "\\t")
                .replace("\n", "\\n");
    }

    private SolanaTransaction mapRow(ResultSet rs) throws SQLException {
        return SolanaTransaction.builder()
                .signature(new Signature(rs.getString("signature")))
                .slot(rs.getLong("slot"))
                .amount(BigDecimal.ZERO)
                .failed(!rs.getBoolean("success"))
                .build();
    }
}
