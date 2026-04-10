package com.stablebridge.prism.domain.service;

import static com.stablebridge.prism.fixtures.TransactionFixtures.transactionBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.then;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stablebridge.prism.domain.model.BatchResult;
import com.stablebridge.prism.domain.model.Signature;
import com.stablebridge.prism.domain.model.SolanaTransaction;
import com.stablebridge.prism.domain.port.FailedTransactionRepository;
import com.stablebridge.prism.domain.port.MemoRepository;
import com.stablebridge.prism.domain.port.MetricsRecorder;
import com.stablebridge.prism.domain.port.TransactionRepository;
import com.stablebridge.prism.domain.port.TransferRepository;

@ExtendWith(MockitoExtension.class)
class TransactionProcessorTest {

    @Mock private TransactionRepository transactionRepository;
    @Mock private FailedTransactionRepository failedTransactionRepository;
    @Mock private TransferRepository transferRepository;
    @Mock private MemoRepository memoRepository;
    @Mock private MetricsRecorder metricsRecorder;
    @InjectMocks private TransactionProcessor processor;

    @Test
    void shouldRouteSuccessfulTransactionsToTransactionRepository() {
        // given
        var tx1 = transactionBuilder().signature(new Signature("5Kx7aEwMbSuccessSig001")).build();
        var tx2 = transactionBuilder().signature(new Signature("5Kx7aEwMbSuccessSig002")).build();
        var tx3 = transactionBuilder().signature(new Signature("5Kx7aEwMbSuccessSig003")).build();
        var batch = List.of(tx1, tx2, tx3);

        // when
        processor.process(batch);

        // then
        then(transactionRepository).should().bulkInsert(batch);
    }

    @Test
    void shouldRouteFailedTransactionsToFailedTransactionRepository() {
        // given
        var tx1 = transactionBuilder()
                .signature(new Signature("5Kx7aEwMbFailedSig0001"))
                .failed(true)
                .build();
        var tx2 = transactionBuilder()
                .signature(new Signature("5Kx7aEwMbFailedSig0002"))
                .failed(true)
                .build();
        var batch = List.of(tx1, tx2);

        // when
        processor.process(batch);

        // then
        var expectedFailed = batch.stream()
                .filter(SolanaTransaction::failed)
                .map(tx -> tx.toFailedTransaction("Transaction failed"))
                .toList();
        then(failedTransactionRepository).should().bulkInsert(expectedFailed);
    }

    @Test
    void shouldRouteMemoTransactionsToMemoRepository() {
        // given
        var tx = transactionBuilder()
                .signature(new Signature("5Kx7aEwMbMemoSig000001"))
                .memo("hello solana")
                .build();
        var batch = List.of(tx);

        // when
        processor.process(batch);

        // then
        var expectedMemos = batch.stream()
                .filter(t -> t.memo() != null)
                .map(SolanaTransaction::toMemo)
                .toList();
        then(memoRepository).should().bulkInsert(expectedMemos);
    }

    @Test
    void shouldIncludeFailedMemoTransactionsInMemoRepository() {
        // given
        var tx = transactionBuilder()
                .signature(new Signature("5Kx7aEwMbFailMemo00001"))
                .failed(true)
                .memo("failed memo")
                .build();
        var batch = List.of(tx);

        // when
        processor.process(batch);

        // then
        var expectedMemos = batch.stream()
                .filter(t -> t.memo() != null)
                .map(SolanaTransaction::toMemo)
                .toList();
        then(memoRepository).should().bulkInsert(expectedMemos);
    }

    @Test
    void shouldRouteLargeTransfersToTransferRepository() {
        // given
        var tx = transactionBuilder()
                .signature(new Signature("5Kx7aEwMbLargeTx000001"))
                .amount(new BigDecimal("5.0"))
                .build();
        var batch = List.of(tx);

        // when
        processor.process(batch);

        // then
        var expectedTransfers = batch.stream()
                .filter(t -> !t.failed() && LargeTransferFilter.isLargeTransfer(t.amount()))
                .map(SolanaTransaction::toLargeTransfer)
                .toList();
        then(transferRepository).should().bulkInsert(expectedTransfers);
    }

    @Test
    void shouldExcludeFailedFromLargeTransfers() {
        // given
        var tx = transactionBuilder()
                .signature(new Signature("5Kx7aEwMbFailLarge0001"))
                .failed(true)
                .amount(new BigDecimal("5.0"))
                .build();
        var batch = List.of(tx);

        // when
        processor.process(batch);

        // then
        then(transferRepository).should().bulkInsert(List.of());
    }

    @Test
    void shouldRecordBatchMetrics() {
        // given
        var tx1 = transactionBuilder().signature(new Signature("5Kx7aEwMbMetricSig0001")).build();
        var tx2 = transactionBuilder()
                .signature(new Signature("5Kx7aEwMbMetricSig0002"))
                .failed(true)
                .build();
        var tx3 = transactionBuilder()
                .signature(new Signature("5Kx7aEwMbMetricSig0003"))
                .memo("metric memo")
                .build();
        var tx4 = transactionBuilder()
                .signature(new Signature("5Kx7aEwMbMetricSig0004"))
                .amount(new BigDecimal("5.0"))
                .build();
        var batch = List.of(tx1, tx2, tx3, tx4);

        // when
        processor.process(batch);

        // then
        var expectedResult = BatchResult.builder()
                .written(4)
                .failed(1)
                .memos(1)
                .transfers(1)
                .build();
        then(metricsRecorder).should().recordBatch(expectedResult);
    }

    @Test
    void shouldReturnBatchResultWithCorrectCounts() {
        // given
        var tx1 = transactionBuilder().signature(new Signature("5Kx7aEwMbCountSig00001")).build();
        var tx2 = transactionBuilder()
                .signature(new Signature("5Kx7aEwMbCountSig00002"))
                .failed(true)
                .build();
        var tx3 = transactionBuilder()
                .signature(new Signature("5Kx7aEwMbCountSig00003"))
                .memo("count memo")
                .build();
        var tx4 = transactionBuilder()
                .signature(new Signature("5Kx7aEwMbCountSig00004"))
                .amount(new BigDecimal("5.0"))
                .build();
        var batch = List.of(tx1, tx2, tx3, tx4);

        // when
        var result = processor.process(batch);

        // then
        var expected = BatchResult.builder()
                .written(4)
                .failed(1)
                .memos(1)
                .transfers(1)
                .build();
        assertThat(result).usingRecursiveComparison().isEqualTo(expected);
    }
}
