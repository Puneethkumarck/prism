package com.stablebridge.prism.infrastructure.persistence;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import javax.sql.DataSource;

import com.stablebridge.prism.domain.model.Account;
import com.stablebridge.prism.domain.model.Pubkey;
import com.stablebridge.prism.domain.port.AccountRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class JdbcAccountRepository implements AccountRepository {

    private static final String UPSERT_SQL = """
            INSERT INTO accounts (pubkey, lamports, slot, executable, rent_epoch) VALUES (?, ?, ?, ?, ?) \
            ON CONFLICT (pubkey) DO UPDATE SET lamports = EXCLUDED.lamports, slot = EXCLUDED.slot, \
            executable = EXCLUDED.executable, rent_epoch = EXCLUDED.rent_epoch""";

    private static final String FIND_BY_PUBKEY_SQL =
            "SELECT pubkey, lamports, slot, executable, rent_epoch FROM accounts WHERE pubkey = ?";

    private final DataSource writePool;
    private final DataSource readPool;

    @Override
    public void batchUpsert(List<Account> accounts) {
        if (accounts.isEmpty()) {
            return;
        }
        try (var conn = writePool.getConnection();
                var ps = conn.prepareStatement(UPSERT_SQL)) {
            for (var account : accounts) {
                ps.setString(1, account.pubkey().value());
                ps.setLong(2, account.lamports());
                ps.setLong(3, account.slot());
                ps.setBoolean(4, account.executable());
                ps.setLong(5, account.rentEpoch());
                ps.addBatch();
            }
            ps.executeBatch();
            log.debug("Batch upserted {} accounts", accounts.size());
        } catch (SQLException e) {
            throw new RuntimeException("Failed to batch upsert accounts", e);
        }
    }

    @Override
    public Optional<Account> findByPubkey(Pubkey pubkey) {
        try (var conn = readPool.getConnection();
                var ps = conn.prepareStatement(FIND_BY_PUBKEY_SQL)) {
            ps.setString(1, pubkey.value());
            try (var rs = ps.executeQuery()) {
                return rs.next() ? Optional.of(mapRow(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new RuntimeException("Failed to find account by pubkey", e);
        }
    }

    private Account mapRow(ResultSet rs) throws SQLException {
        return Account.builder()
                .pubkey(new Pubkey(rs.getString("pubkey")))
                .lamports(rs.getLong("lamports"))
                .slot(rs.getLong("slot"))
                .executable(rs.getBoolean("executable"))
                .rentEpoch(rs.getLong("rent_epoch"))
                .build();
    }
}
