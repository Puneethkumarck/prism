package com.stablebridge.prism.domain.service;

import static com.stablebridge.prism.fixtures.AccountFixtures.accountBuilder;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.BDDMockito.atLeastOnce;
import static org.mockito.BDDMockito.then;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Captor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stablebridge.prism.domain.model.Account;
import com.stablebridge.prism.domain.model.Pubkey;
import com.stablebridge.prism.domain.port.AccountRepository;
import com.stablebridge.prism.domain.port.MetricsRecorder;

@ExtendWith(MockitoExtension.class)
class AccountBatchServiceTest {

    @Mock
    private AccountRepository accountRepository;

    @Mock
    private MetricsRecorder metricsRecorder;

    @Captor
    private ArgumentCaptor<List<Account>> accountsCaptor;

    @Captor
    private ArgumentCaptor<Integer> metricsCaptor;

    private AccountBatchService service;
    private Thread serviceThread;

    @BeforeEach
    void setUp() {
        service = new AccountBatchService(accountRepository, metricsRecorder);
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        service.close();
        if (serviceThread != null) {
            serviceThread.join(5000);
        }
    }

    @Test
    void shouldFlushOnBatchSize() {
        // given
        for (var i = 0; i < 200; i++) {
            service.offer(
                    accountBuilder()
                            .pubkey(new Pubkey("7xKXtg2CBatch" + String.format("%05d", i)))
                            .build());
        }

        // when
        serviceThread = Thread.ofVirtual().start(service::run);

        // then
        await().atMost(5, SECONDS).untilAsserted(() -> {
            then(accountRepository).should(atLeastOnce()).batchUpsert(accountsCaptor.capture());
            var flushed = accountsCaptor.getAllValues().stream().flatMap(List::stream).toList();
            assertThat(flushed).hasSize(200);
            then(metricsRecorder).should(atLeastOnce()).recordAccountsWritten(metricsCaptor.capture());
            var totalRecorded = metricsCaptor.getAllValues().stream().mapToInt(Integer::intValue).sum();
            assertThat(totalRecorded).isEqualTo(200);
        });
    }

    @Test
    void shouldFlushOnTimeout() {
        // given
        serviceThread = Thread.ofVirtual().start(service::run);
        for (var i = 0; i < 50; i++) {
            service.offer(
                    accountBuilder()
                            .pubkey(new Pubkey("7xKXtg2CTime0" + String.format("%05d", i)))
                            .build());
        }

        // when/then
        await().atMost(5, SECONDS).untilAsserted(() -> {
            then(accountRepository).should(atLeastOnce()).batchUpsert(accountsCaptor.capture());
            var flushed = accountsCaptor.getAllValues().stream().flatMap(List::stream).toList();
            assertThat(flushed).hasSize(50);
        });
    }

    @Test
    void shouldDeduplicateByPubkeyKeepingHighestSlot() {
        // given
        var sharedPubkey = new Pubkey("7xKXtg2CDedupPubkey0001");
        service.offer(accountBuilder().pubkey(sharedPubkey).slot(100L).build());
        service.offer(accountBuilder().pubkey(sharedPubkey).slot(300L).build());
        service.offer(accountBuilder().pubkey(sharedPubkey).slot(200L).build());

        // when
        serviceThread = Thread.ofVirtual().start(service::run);

        // then
        await().atMost(5, SECONDS).untilAsserted(() -> {
            then(accountRepository).should(atLeastOnce()).batchUpsert(accountsCaptor.capture());
            var flushed = accountsCaptor.getAllValues().stream().flatMap(List::stream).toList();
            assertThat(flushed).hasSize(1);
            var expected = accountBuilder().pubkey(sharedPubkey).slot(300L).build();
            assertThat(flushed.getFirst()).usingRecursiveComparison().isEqualTo(expected);
        });
    }

    @Test
    void shouldDropWhenQueueFull() {
        // given
        for (var i = 0; i < 10_000; i++) {
            service.offer(
                    accountBuilder()
                            .pubkey(new Pubkey("7xKXtg2CPubkey" + String.format("%05d", i)))
                            .build());
        }

        // when
        var result =
                service.offer(
                        accountBuilder()
                                .pubkey(new Pubkey("7xKXtg2COverflowPubkey01"))
                                .build());

        // then
        assertThat(result).isFalse();
    }

    @Test
    void shouldDrainOnClose() throws InterruptedException {
        // given
        for (var i = 0; i < 10; i++) {
            service.offer(
                    accountBuilder()
                            .pubkey(new Pubkey("7xKXtg2CDrain" + String.format("%05d", i)))
                            .build());
        }

        // when
        service.close();
        serviceThread = Thread.ofVirtual().start(service::run);
        serviceThread.join(5000);

        // then
        then(accountRepository).should(atLeastOnce()).batchUpsert(accountsCaptor.capture());
        var flushed = accountsCaptor.getAllValues().stream().flatMap(List::stream).toList();
        assertThat(flushed).hasSize(10);
    }

    @Test
    void shouldNotFlushEmptyBatch() {
        // given
        serviceThread = Thread.ofVirtual().start(service::run);

        // when
        await().during(3, SECONDS).atMost(5, SECONDS).untilAsserted(() ->
            // then
            then(accountRepository).shouldHaveNoInteractions()
        );
    }
}
