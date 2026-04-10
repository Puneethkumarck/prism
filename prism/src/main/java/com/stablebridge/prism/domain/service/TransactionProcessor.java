package com.stablebridge.prism.domain.service;

import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;

import com.stablebridge.prism.domain.exception.BatchProcessingException;
import com.stablebridge.prism.domain.model.BatchResult;
import com.stablebridge.prism.domain.model.SolanaTransaction;
import com.stablebridge.prism.domain.port.FailedTransactionRepository;
import com.stablebridge.prism.domain.port.MemoRepository;
import com.stablebridge.prism.domain.port.MetricsRecorder;
import com.stablebridge.prism.domain.port.TransactionRepository;
import com.stablebridge.prism.domain.port.TransferRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RequiredArgsConstructor
public class TransactionProcessor {

    private final TransactionRepository transactionRepository;
    private final FailedTransactionRepository failedTransactionRepository;
    private final TransferRepository transferRepository;
    private final MemoRepository memoRepository;
    private final MetricsRecorder metricsRecorder;

    public BatchResult process(List<SolanaTransaction> batch) {
        var successful = batch.stream().filter(tx -> !tx.failed()).toList();

        var failedTxs = batch.stream()
                .filter(SolanaTransaction::failed)
                .map(tx -> tx.toFailedTransaction("Transaction failed"))
                .toList();

        var memos = batch.stream()
                .filter(tx -> tx.memo() != null)
                .map(SolanaTransaction::toMemo)
                .toList();

        var transfers = batch.stream()
                .filter(tx -> !tx.failed() && LargeTransferFilter.isLargeTransfer(tx.amount()))
                .map(SolanaTransaction::toLargeTransfer)
                .toList();

        try (var executor = Executors.newVirtualThreadPerTaskExecutor()) {
            var f1 = executor.submit(() -> transactionRepository.bulkInsert(successful));
            var f2 = executor.submit(() -> failedTransactionRepository.bulkInsert(failedTxs));
            var f3 = executor.submit(() -> memoRepository.bulkInsert(memos));
            var f4 = executor.submit(() -> transferRepository.bulkInsert(transfers));
            f1.get();
            f2.get();
            f3.get();
            f4.get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new BatchProcessingException("Batch processing interrupted", e);
        } catch (ExecutionException e) {
            throw new BatchProcessingException("Batch processing failed", e.getCause());
        }

        var result = BatchResult.builder()
                .written(successful.size())
                .failed(failedTxs.size())
                .memos(memos.size())
                .transfers(transfers.size())
                .build();

        metricsRecorder.recordBatch(result);

        return result;
    }
}
