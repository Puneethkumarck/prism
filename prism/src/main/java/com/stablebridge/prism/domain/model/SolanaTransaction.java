package com.stablebridge.prism.domain.model;

import java.math.BigDecimal;
import java.util.Objects;

import lombok.Builder;

@Builder(toBuilder = true)
public record SolanaTransaction(
        Signature signature,
        long slot,
        BigDecimal amount,
        boolean failed,
        String memo,
        Pubkey from,
        Pubkey to
) {

    public SolanaTransaction {
        Objects.requireNonNull(signature, "signature must not be null");
        Objects.requireNonNull(amount, "amount must not be null");
        if (slot < 0) {
            throw new IllegalArgumentException("slot must not be negative");
        }
    }

    public LargeTransfer toLargeTransfer() {
        return LargeTransfer.builder()
                .signature(signature)
                .slot(slot)
                .amount(amount)
                .build();
    }

    public Memo toMemo() {
        Objects.requireNonNull(memo, "cannot create Memo from transaction without memo");
        return Memo.builder()
                .signature(signature)
                .memoText(memo)
                .build();
    }

    public FailedTransaction toFailedTransaction(String error) {
        return FailedTransaction.builder()
                .signature(signature)
                .slot(slot)
                .error(error)
                .build();
    }
}
