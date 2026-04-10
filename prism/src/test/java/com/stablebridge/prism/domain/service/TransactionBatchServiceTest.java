package com.stablebridge.prism.domain.service;

import static com.stablebridge.prism.fixtures.TransactionFixtures.transactionBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.atLeastOnce;
import static org.mockito.BDDMockito.then;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stablebridge.prism.domain.model.Signature;
import com.stablebridge.prism.domain.model.SolanaTransaction;

@ExtendWith(MockitoExtension.class)
class TransactionBatchServiceTest {

    @Mock private TransactionProcessor processor;
    @Captor private ArgumentCaptor<List<SolanaTransaction>> batchCaptor;
    private TransactionBatchService batchService;

    @BeforeEach
    void setUp() {
        batchService = new TransactionBatchService(processor);
    }

    @Test
    void shouldFlushWhenBatchSizeReached() throws Exception {
        // given
        var txs = IntStream.range(0, 200)
                .mapToObj(i -> transactionBuilder()
                        .signature(new Signature("5Kx7aEwMbBatch" + String.format("%04d", i)))
                        .build())
                .toList();

        // when
        txs.forEach(batchService::enqueue);
        var thread = Thread.ofVirtual().start(() -> batchService.run());
        Thread.sleep(500);
        batchService.close();
        thread.join(5000);

        // then
        then(processor).should(atLeastOnce()).process(batchCaptor.capture());
        var totalProcessed = batchCaptor.getAllValues().stream().mapToInt(List::size).sum();
        assertThat(totalProcessed).isEqualTo(200);
    }

    @Test
    void shouldFlushOnTimeoutWhenBelowBatchSize() throws Exception {
        // given
        var txs = IntStream.range(0, 50)
                .mapToObj(i -> transactionBuilder()
                        .signature(new Signature("5Kx7aEwMbTime0" + String.format("%04d", i)))
                        .build())
                .toList();

        // when
        txs.forEach(batchService::enqueue);
        var thread = Thread.ofVirtual().start(() -> batchService.run());
        Thread.sleep(300);
        batchService.close();
        thread.join(5000);

        // then
        then(processor).should(atLeastOnce()).process(batchCaptor.capture());
        var totalProcessed = batchCaptor.getAllValues().stream().mapToInt(List::size).sum();
        assertThat(totalProcessed).isEqualTo(50);
    }

    @Test
    void shouldDrainRemainingOnClose() throws Exception {
        // given
        var txs = IntStream.range(0, 10)
                .mapToObj(i -> transactionBuilder()
                        .signature(new Signature("5Kx7aEwMbDrain" + String.format("%04d", i)))
                        .build())
                .toList();

        // when
        txs.forEach(batchService::enqueue);
        batchService.close();
        var thread = Thread.ofVirtual().start(() -> batchService.run());
        thread.join(5000);

        // then
        then(processor).should(atLeastOnce()).process(batchCaptor.capture());
        var totalProcessed = batchCaptor.getAllValues().stream().mapToInt(List::size).sum();
        assertThat(totalProcessed).isEqualTo(10);
    }

    @Test
    void shouldNotFlushEmptyBatch() throws Exception {
        // given
        var thread = Thread.ofVirtual().start(() -> batchService.run());

        // when
        Thread.sleep(300);
        batchService.close();
        thread.join(5000);

        // then
        then(processor).shouldHaveNoInteractions();
    }

    @Test
    void shouldUseUnboundedQueue() throws Exception {
        // given
        var txs = IntStream.range(0, 10_000)
                .mapToObj(i -> transactionBuilder()
                        .signature(new Signature("5Kx7aEwMbUnbd" + String.format("%05d", i)))
                        .build())
                .toList();

        // when
        txs.forEach(batchService::enqueue);
        var thread = Thread.ofVirtual().start(() -> batchService.run());
        Thread.sleep(3000);
        batchService.close();
        thread.join(5000);

        // then
        then(processor).should(atLeastOnce()).process(batchCaptor.capture());
        var totalProcessed = batchCaptor.getAllValues().stream().mapToInt(List::size).sum();
        assertThat(totalProcessed).isEqualTo(10_000);
    }
}
