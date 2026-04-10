package com.stablebridge.prism.domain.port;

import java.util.List;
import java.util.Optional;

import com.stablebridge.prism.domain.model.Account;
import com.stablebridge.prism.domain.model.Pubkey;

public interface AccountRepository {

    void batchUpsert(List<Account> accounts);

    Optional<Account> findByPubkey(Pubkey pubkey);
}
