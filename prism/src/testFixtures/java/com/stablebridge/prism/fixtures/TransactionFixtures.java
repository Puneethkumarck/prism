package com.stablebridge.prism.fixtures;

import java.math.BigDecimal;
import java.util.UUID;

import com.stablebridge.prism.domain.model.FailedTransaction;
import com.stablebridge.prism.domain.model.LargeTransfer;
import com.stablebridge.prism.domain.model.Memo;
import com.stablebridge.prism.domain.model.SolanaTransaction;

public final class TransactionFixtures {

    private TransactionFixtures() {}

    public static SolanaTransaction.SolanaTransactionBuilder transactionBuilder() {
        return SolanaTransaction.builder()
                .signature("5Kx7aEwMb" + UUID.randomUUID().toString().substring(0, 8))
                .slot(280_000_000L)
                .amount(new BigDecimal("0.5"))
                .failed(false)
                .from("SenderPubkey1234abcd5678")
                .to("ReceiverPubkey12efgh5678");
    }

    public static final SolanaTransaction SOME_TRANSACTION = transactionBuilder().build();

    public static final SolanaTransaction SOME_FAILED_TRANSACTION = transactionBuilder()
            .signature("5Kx7aEwMbFailedSig00001")
            .failed(true)
            .build();

    public static final SolanaTransaction SOME_MEMO_TRANSACTION = transactionBuilder()
            .signature("5Kx7aEwMbMemoSignature01")
            .memo("hello solana")
            .build();

    public static final SolanaTransaction SOME_LARGE_TRANSFER = transactionBuilder()
            .signature("5Kx7aEwMbLargeTransfer01")
            .amount(new BigDecimal("5.0"))
            .build();

    public static LargeTransfer.LargeTransferBuilder largeTransferBuilder() {
        return LargeTransfer.builder()
                .signature("5Kx7aEwMbLargeTransfer01")
                .slot(280_000_000L)
                .amount(new BigDecimal("5.0"));
    }

    public static final LargeTransfer SOME_LARGE_TRANSFER_RECORD = largeTransferBuilder().build();

    public static Memo.MemoBuilder memoBuilder() {
        return Memo.builder()
                .signature("5Kx7aEwMbMemoSignature01")
                .memoText("hello solana");
    }

    public static final Memo SOME_MEMO = memoBuilder().build();

    public static FailedTransaction.FailedTransactionBuilder failedTransactionBuilder() {
        return FailedTransaction.builder()
                .signature("5Kx7aEwMbFailedSig00001")
                .slot(280_000_000L)
                .error("InstructionError");
    }

    public static final FailedTransaction SOME_FAILED_TRANSACTION_RECORD = failedTransactionBuilder().build();
}
