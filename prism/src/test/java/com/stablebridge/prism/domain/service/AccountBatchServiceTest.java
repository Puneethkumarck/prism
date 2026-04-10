package com.stablebridge.prism.domain.service;

import static com.stablebridge.prism.fixtures.AccountFixtures.accountBuilder;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.atLeastOnce;
import static org.mockito.BDDMockito.then;

import java.util.List;

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

    @Test
    void shouldFlushOnBatchSize() throws InterruptedException {
        // given
        var service = new AccountBatchService(accountRepository, metricsRecorder);
        for (var i = 0; i < 200; i++) {
            service.offer(
                    accountBuilder()
                            .pubkey(new Pubkey("7xKXtg2CBatch" + String.format("%05d", i)))
                            .build());
        }

        // when
        var thread = Thread.ofVirtual().start(service::run);
        Thread.sleep(500);
        service.close();
        thread.join();

        // then
        then(accountRepository).should(atLeastOnce()).batchUpsert(accountsCaptor.capture());
        var flushed = accountsCaptor.getAllValues().stream().flatMap(List::stream).toList();
        assertThat(flushed).hasSize(200);
    }

    @Test
    void shouldFlushOnTimeout() throws InterruptedException {
        // given
        var service = new AccountBatchService(accountRepository, metricsRecorder);
        for (var i = 0; i < 50; i++) {
            service.offer(
                    accountBuilder()
                            .pubkey(new Pubkey("7xKXtg2CTime0" + String.format("%05d", i)))
                            .build());
        }

        // when
        var thread = Thread.ofVirtual().start(service::run);
        Thread.sleep(3000);
        service.close();
        thread.join();

        // then
        then(accountRepository).should(atLeastOnce()).batchUpsert(accountsCaptor.capture());
        var flushed = accountsCaptor.getAllValues().stream().flatMap(List::stream).toList();
        assertThat(flushed).hasSize(50);
    }

    @Test
    void shouldDeduplicateByPubkeyKeepingHighestSlot() throws InterruptedException {
        // given
        var service = new AccountBatchService(accountRepository, metricsRecorder);
        var sharedPubkey = new Pubkey("7xKXtg2CDedupPubkey0001");
        service.offer(accountBuilder().pubkey(sharedPubkey).slot(100L).build());
        service.offer(accountBuilder().pubkey(sharedPubkey).slot(300L).build());
        service.offer(accountBuilder().pubkey(sharedPubkey).slot(200L).build());

        // when
        var thread = Thread.ofVirtual().start(service::run);
        Thread.sleep(3000);
        service.close();
        thread.join();

        // then
        then(accountRepository).should(atLeastOnce()).batchUpsert(accountsCaptor.capture());
        var flushed = accountsCaptor.getAllValues().stream().flatMap(List::stream).toList();
        assertThat(flushed).hasSize(1);
        var expected = accountBuilder().pubkey(sharedPubkey).slot(300L).build();
        assertThat(flushed.getFirst()).usingRecursiveComparison().isEqualTo(expected);
    }

    @Test
    void shouldDropWhenQueueFull() {
        // given
        var service = new AccountBatchService(accountRepository, metricsRecorder);
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
        var service = new AccountBatchService(accountRepository, metricsRecorder);
        for (var i = 0; i < 10; i++) {
            service.offer(
                    accountBuilder()
                            .pubkey(new Pubkey("7xKXtg2CDrain" + String.format("%05d", i)))
                            .build());
        }

        // when
        service.close();
        var thread = Thread.ofVirtual().start(service::run);
        thread.join();

        // then
        then(accountRepository).should(atLeastOnce()).batchUpsert(accountsCaptor.capture());
        var flushed = accountsCaptor.getAllValues().stream().flatMap(List::stream).toList();
        assertThat(flushed).hasSize(10);
    }
}
