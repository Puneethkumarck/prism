package com.stablebridge.prism.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;

import com.stablebridge.prism.domain.model.Memo;
import com.stablebridge.prism.domain.model.Signature;
import com.stablebridge.prism.domain.port.MemoRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class JdbcMemoRepository implements MemoRepository {

    private static final String INSERT_SQL = "INSERT INTO memos (signature, memo) VALUES (?, ?)";

    private static final String FIND_ALL_SQL =
            "SELECT signature, memo FROM memos ORDER BY created_at DESC LIMIT ? OFFSET ?";

    private static final String COUNT_SQL = "SELECT COUNT(*) FROM memos";

    private final DataSource writePool;
    private final DataSource readPool;

    @Override
    public void bulkInsert(List<Memo> memos) {
        if (memos.isEmpty()) {
            return;
        }
        try (var conn = writePool.getConnection()) {
            conn.setAutoCommit(false);
            try (var ps = conn.prepareStatement(INSERT_SQL)) {
                for (var memo : memos) {
                    ps.setString(1, memo.signature().value());
                    ps.setString(2, memo.memoText());
                    ps.addBatch();
                }
                ps.executeBatch();
                conn.commit();
            } catch (SQLException e) {
                conn.rollback();
                throw e;
            }
            log.debug("Batch inserted {} memos", memos.size());
        } catch (SQLException e) {
            throw new RuntimeException("Failed to bulk insert memos", e);
        }
    }

    @Override
    public List<Memo> findAll(long limit, long offset) {
        if (limit < 0) {
            throw new IllegalArgumentException("limit must be non-negative");
        }
        if (offset < 0) {
            throw new IllegalArgumentException("offset must be non-negative");
        }
        try (var conn = readPool.getConnection();
                var ps = conn.prepareStatement(FIND_ALL_SQL)) {
            ps.setLong(1, limit);
            ps.setLong(2, offset);
            try (var rs = ps.executeQuery()) {
                var results = new ArrayList<Memo>();
                while (rs.next()) {
                    results.add(mapRow(rs));
                }
                return results;
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find memos", e);
        }
    }

    @Override
    public long countAll() {
        try (var conn = readPool.getConnection();
                var ps = conn.prepareStatement(COUNT_SQL);
                var rs = ps.executeQuery()) {
            rs.next();
            return rs.getLong(1);
        } catch (SQLException e) {
            throw new RuntimeException("Failed to count memos", e);
        }
    }

    private Memo mapRow(ResultSet rs) throws SQLException {
        return Memo.builder()
                .signature(new Signature(rs.getString("signature")))
                .memoText(rs.getString("memo"))
                .build();
    }
}
