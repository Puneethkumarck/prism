package com.stablebridge.prism.domain.port;

import java.util.List;
import java.util.Optional;

import com.stablebridge.prism.domain.model.Signature;
import com.stablebridge.prism.domain.model.SolanaTransaction;

public interface TransactionRepository {

    void bulkInsert(List<SolanaTransaction> batch);

    Optional<SolanaTransaction> findBySignature(Signature signature);

    List<SolanaTransaction> findBySlot(long slot);

    List<SolanaTransaction> findAll(long limit, long offset, Boolean success);

    long countAll();

    long countBySuccess(boolean success);
}
