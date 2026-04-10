package com.stablebridge.prism.domain.port;

import java.util.List;

import com.stablebridge.prism.domain.model.FailedTransaction;

public interface FailedTransactionRepository {

    void bulkInsert(List<FailedTransaction> batch);
}
