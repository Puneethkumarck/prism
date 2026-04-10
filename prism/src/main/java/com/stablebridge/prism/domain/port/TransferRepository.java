package com.stablebridge.prism.domain.port;

import java.util.List;

import com.stablebridge.prism.domain.model.LargeTransfer;

public interface TransferRepository {

    void bulkInsert(List<LargeTransfer> transfers);

    List<LargeTransfer> findByMinAmount(double minAmount, long limit, long offset);

    long countByMinAmount(double minAmount);
}
