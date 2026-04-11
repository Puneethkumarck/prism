package com.stablebridge.prism.domain.service;

import static com.stablebridge.prism.fixtures.TransactionFixtures.transactionBuilder;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;
import static org.mockito.BDDMockito.atLeastOnce;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
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

    @Mock
    private TransactionProcessor processor;

    @Captor
    private ArgumentCaptor<List<SolanaTransaction>> batchCaptor;

    private TransactionBatchService batchService;
    private Thread serviceThread;

    @BeforeEach
    void setUp() {
        batchService = new TransactionBatchService(processor);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        batchService.close();
        if (serviceThread != null) {
            serviceThread.join(5_000);
        }
    }

    @Nested
    class BatchTrigger {

        @Test
        void shouldFlushWhenBatchSizeReached() {
            // given
            var txs = IntStream.range(0, 200)
                    .mapToObj(i -> transactionBuilder()
                            .signature(new Signature("5Kx7aEwMbBatch" + String.format("%04d", i)))
                            .build())
                    .toList();
            txs.forEach(batchService::enqueue);

            // when
            serviceThread = Thread.ofVirtual().start(batchService::run);

            // then
            await().atMost(5, SECONDS).untilAsserted(() -> {
                then(processor).should(atLeastOnce()).process(batchCaptor.capture());
                var batches = batchCaptor.getAllValues();
                var totalProcessed = batches.stream().mapToInt(List::size).sum();
                assertThat(totalProcessed).isEqualTo(200);
                assertThat(batches).anyMatch(batch -> batch.size() == TransactionBatchService.BATCH_SIZE);
            });
        }

        @Test
        void shouldUseUnboundedQueue() {
            // given
            var txs = IntStream.range(0, 10_000)
                    .mapToObj(i -> transactionBuilder()
                            .signature(new Signature("5Kx7aEwMbUnbd" + String.format("%05d", i)))
                            .build())
                    .toList();
            txs.forEach(batchService::enqueue);

            // when
            serviceThread = Thread.ofVirtual().start(batchService::run);

            // then
            await().atMost(10, SECONDS).untilAsserted(() -> {
                then(processor).should(atLeastOnce()).process(batchCaptor.capture());
                var totalProcessed =
                        batchCaptor.getAllValues().stream().mapToInt(List::size).sum();
                assertThat(totalProcessed).isEqualTo(10_000);
            });
        }
    }

    @Nested
    class TimeoutTrigger {

        @Test
        void shouldFlushOnTimeoutWhenBelowBatchSize() {
            // given
            var txs = IntStream.range(0, 50)
                    .mapToObj(i -> transactionBuilder()
                            .signature(new Signature("5Kx7aEwMbTime0" + String.format("%04d", i)))
                            .build())
                    .toList();
            txs.forEach(batchService::enqueue);

            // when
            serviceThread = Thread.ofVirtual().start(batchService::run);

            // then
            await().atMost(5, SECONDS).untilAsserted(() -> {
                then(processor).should(atLeastOnce()).process(batchCaptor.capture());
                var totalProcessed =
                        batchCaptor.getAllValues().stream().mapToInt(List::size).sum();
                assertThat(totalProcessed).isEqualTo(50);
            });
        }

        @Test
        void shouldNotFlushEmptyBatch() {
            // given
            serviceThread = Thread.ofVirtual().start(batchService::run);

            // when/then
            await().during(2, SECONDS).atMost(3, SECONDS).untilAsserted(() ->
                    then(processor).shouldHaveNoInteractions());
        }
    }

    @Nested
    class Lifecycle {

        @Test
        void shouldDrainRemainingOnClose() throws InterruptedException {
            // given
            var txs = IntStream.range(0, 10)
                    .mapToObj(i -> transactionBuilder()
                            .signature(new Signature("5Kx7aEwMbDrain" + String.format("%04d", i)))
                            .build())
                    .toList();
            txs.forEach(batchService::enqueue);

            // when
            batchService.close();
            serviceThread = Thread.ofVirtual().start(batchService::run);
            serviceThread.join(5_000);

            // then
            then(processor).should(atLeastOnce()).process(batchCaptor.capture());
            var totalProcessed =
                    batchCaptor.getAllValues().stream().mapToInt(List::size).sum();
            assertThat(totalProcessed).isEqualTo(10);
        }

        @Test
        void shouldRejectNullEnqueue() {
            // when/then
            assertThatThrownBy(() -> batchService.enqueue(null))
                    .isInstanceOf(NullPointerException.class)
                    .hasMessageContaining("tx");
        }
    }

    @Nested
    class Resilience {

        @Test
        void shouldContinueProcessingAfterFailedBatch() {
            // given
            var firstBatch = IntStream.range(0, 50)
                    .mapToObj(i -> transactionBuilder()
                            .signature(new Signature("5Kx7aEwMbFailA" + String.format("%04d", i)))
                            .build())
                    .toList();
            var secondBatch = IntStream.range(0, 30)
                    .mapToObj(i -> transactionBuilder()
                            .signature(new Signature("5Kx7aEwMbFailB" + String.format("%04d", i)))
                            .build())
                    .toList();
            given(processor.process(firstBatch)).willThrow(new RuntimeException("simulated processor failure"));
            firstBatch.forEach(batchService::enqueue);

            // when
            serviceThread = Thread.ofVirtual().start(batchService::run);
            await().atMost(5, SECONDS).untilAsserted(() ->
                    then(processor).should(atLeastOnce()).process(firstBatch));
            secondBatch.forEach(batchService::enqueue);

            // then
            await().atMost(5, SECONDS).untilAsserted(() ->
                    then(processor).should(atLeastOnce()).process(secondBatch));
        }
    }
}
