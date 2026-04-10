package com.stablebridge.prism.domain.port;

import java.math.BigDecimal;
import java.util.List;

import com.stablebridge.prism.domain.model.LargeTransfer;

public interface TransferRepository {

    void bulkInsert(List<LargeTransfer> transfers);

    List<LargeTransfer> findByMinAmount(BigDecimal minAmount, long limit, long offset);

    long countByMinAmount(BigDecimal minAmount);
}
