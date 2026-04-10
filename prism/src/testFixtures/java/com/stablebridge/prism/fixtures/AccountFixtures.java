package com.stablebridge.prism.fixtures;

import java.util.UUID;

import com.stablebridge.prism.domain.model.Account;

public final class AccountFixtures {

    private AccountFixtures() {}

    public static Account.AccountBuilder accountBuilder() {
        return Account.builder()
                .pubkey("7xKXtg2C" + UUID.randomUUID().toString().substring(0, 8))
                .lamports(1_000_000_000L)
                .slot(280_000_000L)
                .executable(false)
                .rentEpoch(0);
    }

    public static final Account SOME_ACCOUNT = accountBuilder().build();

    public static final Account SOME_EXECUTABLE_ACCOUNT = accountBuilder()
            .pubkey("7xKXtg2CExecPubkey00001")
            .executable(true)
            .build();
}
