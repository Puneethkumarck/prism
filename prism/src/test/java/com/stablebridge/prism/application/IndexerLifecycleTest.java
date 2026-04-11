package com.stablebridge.prism.application;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.BDDMockito.willThrow;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.stablebridge.prism.domain.port.TransactionStream;
import com.stablebridge.prism.domain.service.AccountBatchService;
import com.stablebridge.prism.domain.service.TransactionBatchService;
import com.stablebridge.prism.infrastructure.metrics.BenchmarkLogReporter;

import io.helidon.webserver.WebServer;

@ExtendWith(MockitoExtension.class)
class IndexerLifecycleTest {

    @Mock
    private WebServer server;

    @Mock
    private ExecutorService executor;

    @Mock
    private TransactionStream stream;

    @Mock
    private TransactionBatchService txBatchService;

    @Mock
    private AccountBatchService acctBatchService;

    @Mock
    private BenchmarkLogReporter benchmarkReporter;

    private IndexerLifecycle lifecycle;

    @BeforeEach
    void setUp() {
        lifecycle = new IndexerLifecycle(
                server, executor, stream, txBatchService, acctBatchService, benchmarkReporter);
    }

    @Nested
    class Stop {

        @Test
        void shouldInvokeEveryCloseStepInOrder() throws InterruptedException {
            // given
            given(executor.awaitTermination(IndexerLifecycle.SHUTDOWN_TIMEOUT_SECS, TimeUnit.SECONDS))
                    .willReturn(true);

            // when
            lifecycle.stop();

            // then
            then(stream).should().close();
            then(txBatchService).should().close();
            then(acctBatchService).should().close();
            then(benchmarkReporter).should().close();
            then(executor).should().shutdown();
            then(executor).should().awaitTermination(IndexerLifecycle.SHUTDOWN_TIMEOUT_SECS, TimeUnit.SECONDS);
            then(server).should().stop();
        }

        @Test
        void shouldContinueShutdownWhenStreamCloseThrows() throws InterruptedException {
            // given
            willThrow(new RuntimeException("stream close failed")).given(stream).close();
            given(executor.awaitTermination(IndexerLifecycle.SHUTDOWN_TIMEOUT_SECS, TimeUnit.SECONDS))
                    .willReturn(true);

            // when
            lifecycle.stop();

            // then
            then(txBatchService).should().close();
            then(acctBatchService).should().close();
            then(benchmarkReporter).should().close();
            then(executor).should().shutdown();
            then(server).should().stop();
        }

        @Test
        void shouldContinueShutdownWhenBatchServiceCloseThrows() throws InterruptedException {
            // given
            willThrow(new RuntimeException("tx close failed")).given(txBatchService).close();
            willThrow(new RuntimeException("acct close failed")).given(acctBatchService).close();
            given(executor.awaitTermination(IndexerLifecycle.SHUTDOWN_TIMEOUT_SECS, TimeUnit.SECONDS))
                    .willReturn(true);

            // when
            lifecycle.stop();

            // then
            then(stream).should().close();
            then(benchmarkReporter).should().close();
            then(executor).should().shutdown();
            then(server).should().stop();
        }

        @Test
        void shouldForceShutdownWhenExecutorDoesNotTerminateInTime() throws InterruptedException {
            // given
            given(executor.awaitTermination(IndexerLifecycle.SHUTDOWN_TIMEOUT_SECS, TimeUnit.SECONDS))
                    .willReturn(false);

            // when
            lifecycle.stop();

            // then
            then(executor).should().shutdown();
            then(executor).should().shutdownNow();
            then(server).should().stop();
        }

        @Test
        void shouldBeIdempotent() throws InterruptedException {
            // given
            given(executor.awaitTermination(IndexerLifecycle.SHUTDOWN_TIMEOUT_SECS, TimeUnit.SECONDS))
                    .willReturn(true);

            // when
            lifecycle.stop();
            lifecycle.stop();

            // then
            then(stream).should().close();
            then(server).should().stop();
        }
    }

    @Nested
    class IsRunning {

        @Test
        void shouldReportRunningWhenServerIsRunning() {
            // given
            given(server.isRunning()).willReturn(true);

            // when
            var running = lifecycle.isRunning();

            // then
            assertThat(running).isTrue();
        }

        @Test
        void shouldReportNotRunningAfterStop() throws InterruptedException {
            // given
            given(executor.awaitTermination(IndexerLifecycle.SHUTDOWN_TIMEOUT_SECS, TimeUnit.SECONDS))
                    .willReturn(true);
            lifecycle.stop();

            // when
            var running = lifecycle.isRunning();

            // then
            assertThat(running).isFalse();
        }
    }
}
