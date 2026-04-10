package com.stablebridge.prism.domain.port;

import java.util.function.Consumer;

import com.stablebridge.prism.domain.model.Account;
import com.stablebridge.prism.domain.model.SolanaTransaction;

public interface TransactionStream {

    void subscribe(Consumer<SolanaTransaction> txConsumer, Consumer<Account> acctConsumer);

    void close();
}
