package com.stablebridge.prism.application;

import java.time.Instant;
import java.util.Objects;
import java.util.concurrent.Executors;
import javax.sql.DataSource;

import org.mapstruct.factory.Mappers;

import com.stablebridge.prism.application.config.IndexerConfig;
import com.stablebridge.prism.application.error.GlobalErrorHandler;
import com.stablebridge.prism.application.mapper.AccountResponseMapper;
import com.stablebridge.prism.application.mapper.MemoResponseMapper;
import com.stablebridge.prism.application.mapper.StatsResponseMapper;
import com.stablebridge.prism.application.mapper.TransactionResponseMapper;
import com.stablebridge.prism.application.mapper.TransferResponseMapper;
import com.stablebridge.prism.application.route.AccountRoutes;
import com.stablebridge.prism.application.route.CorsConfiguration;
import com.stablebridge.prism.application.route.HealthRoutes;
import com.stablebridge.prism.application.route.MemoRoutes;
import com.stablebridge.prism.application.route.StatsRoutes;
import com.stablebridge.prism.application.route.TransactionRoutes;
import com.stablebridge.prism.application.route.TransferRoutes;
import com.stablebridge.prism.domain.port.TransactionStream;
import com.stablebridge.prism.domain.service.AccountBatchService;
import com.stablebridge.prism.domain.service.TransactionBatchService;
import com.stablebridge.prism.domain.service.TransactionProcessor;
import com.stablebridge.prism.infrastructure.grpc.GrpcChannelFactory;
import com.stablebridge.prism.infrastructure.grpc.ReconnectHandler;
import com.stablebridge.prism.infrastructure.grpc.TransactionParser;
import com.stablebridge.prism.infrastructure.grpc.YellowstoneTransactionStream;
import com.stablebridge.prism.infrastructure.metrics.BenchmarkLogReporter;
import com.stablebridge.prism.infrastructure.metrics.MicrometerMetricsRecorder;
import com.stablebridge.prism.infrastructure.persistence.CopyTransactionRepository;
import com.stablebridge.prism.infrastructure.persistence.DataSourceFactory;
import com.stablebridge.prism.infrastructure.persistence.FlywayMigrator;
import com.stablebridge.prism.infrastructure.persistence.JdbcAccountRepository;
import com.stablebridge.prism.infrastructure.persistence.JdbcFailedTransactionRepository;
import com.stablebridge.prism.infrastructure.persistence.JdbcMemoRepository;
import com.stablebridge.prism.infrastructure.persistence.JdbcStatsRepository;
import com.stablebridge.prism.infrastructure.persistence.JdbcTransferRepository;
import com.stablebridge.prism.infrastructure.websocket.BlockNotificationParser;
import com.stablebridge.prism.infrastructure.websocket.WebSocketTransactionStream;
import com.zaxxer.hikari.HikariDataSource;

import io.helidon.common.media.type.MediaTypes;
import io.helidon.webserver.WebServer;
import io.micrometer.prometheusmetrics.PrometheusConfig;
import io.micrometer.prometheusmetrics.PrometheusMeterRegistry;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public final class IndexerApplication {

    static final long BENCHMARK_INTERVAL_SECS = 300L;

    private IndexerApplication() {}

    public static void main(String[] args) {
        var config = IndexerConfig.fromEnv();
        var writePool = DataSourceFactory.createWritePool(config.databaseUrl());
        var readPool = DataSourceFactory.createReadPool(config.databaseUrl());
        FlywayMigrator.migrate(writePool);
        var stream = buildStream(config);
        var lifecycle = start(config, writePool, readPool, stream);
        Runtime.getRuntime()
                .addShutdownHook(Thread.ofPlatform().name("prism-shutdown").unstarted(() -> {
                    lifecycle.stop();
                    closeQuietly(writePool);
                    closeQuietly(readPool);
                }));
        log.info("Prism indexer started on port {}", lifecycle.port());
    }

    static IndexerLifecycle start(
            IndexerConfig config, DataSource writePool, DataSource readPool, TransactionStream stream) {
        Objects.requireNonNull(config, "config must not be null");
        Objects.requireNonNull(writePool, "writePool must not be null");
        Objects.requireNonNull(readPool, "readPool must not be null");
        Objects.requireNonNull(stream, "stream must not be null");

        var transactionRepository = new CopyTransactionRepository(writePool, readPool);
        var failedTransactionRepository = new JdbcFailedTransactionRepository(writePool);
        var transferRepository = new JdbcTransferRepository(writePool, readPool);
        var memoRepository = new JdbcMemoRepository(writePool, readPool);
        var accountRepository = new JdbcAccountRepository(writePool, readPool);
        var statsRepository = new JdbcStatsRepository(readPool);

        var prometheusRegistry = new PrometheusMeterRegistry(PrometheusConfig.DEFAULT);
        var metricsRecorder = new MicrometerMetricsRecorder(prometheusRegistry);

        var processor = new TransactionProcessor(
                transactionRepository, failedTransactionRepository, transferRepository, memoRepository, metricsRecorder);
        var txBatchService = new TransactionBatchService(processor);
        var acctBatchService = new AccountBatchService(accountRepository, metricsRecorder);
        var benchmarkReporter =
                new BenchmarkLogReporter(metricsRecorder, config.benchLog(), BENCHMARK_INTERVAL_SECS);

        var executor = Executors.newVirtualThreadPerTaskExecutor();
        executor.submit(txBatchService::run);
        executor.submit(acctBatchService::run);
        executor.submit(benchmarkReporter::run);

        stream.subscribe(
                tx -> {
                    metricsRecorder.incrementReceived();
                    txBatchService.enqueue(tx);
                },
                account -> {
                    if (!acctBatchService.offer(account)) {
                        log.debug("account queue full; dropping pubkey {}", account.pubkey().value());
                    }
                });

        var healthRoutes = new HealthRoutes(Instant.now().getEpochSecond());
        var statsRoutes = new StatsRoutes(statsRepository, Mappers.getMapper(StatsResponseMapper.class));
        var transactionRoutes = new TransactionRoutes(
                transactionRepository, Mappers.getMapper(TransactionResponseMapper.class));
        var transferRoutes = new TransferRoutes(transferRepository, Mappers.getMapper(TransferResponseMapper.class));
        var memoRoutes = new MemoRoutes(memoRepository, Mappers.getMapper(MemoResponseMapper.class));
        var accountRoutes = new AccountRoutes(accountRepository, Mappers.getMapper(AccountResponseMapper.class));

        var server = WebServer.builder()
                .port(config.apiPort())
                .routing(r -> {
                    r.register(CorsConfiguration.permissive())
                            .register(healthRoutes)
                            .register(statsRoutes)
                            .register(transactionRoutes)
                            .register(transferRoutes)
                            .register(memoRoutes)
                            .register(accountRoutes)
                            .get("/metrics", (req, res) -> {
                                res.headers().contentType(MediaTypes.TEXT_PLAIN);
                                res.send(prometheusRegistry.scrape());
                            });
                    GlobalErrorHandler.register(r);
                })
                .build()
                .start();

        return new IndexerLifecycle(server, executor, stream, txBatchService, acctBatchService);
    }

    private static TransactionStream buildStream(IndexerConfig config) {
        if (IndexerConfig.STREAM_MODE_GRPC.equals(config.streamMode())) {
            var channel = GrpcChannelFactory.create(config.grpcEndpoint());
            return new YellowstoneTransactionStream(
                    channel, new TransactionParser(), new ReconnectHandler(), config.xToken());
        }
        return new WebSocketTransactionStream(
                config.rpcWsEndpoint(), new BlockNotificationParser(), new ReconnectHandler());
    }

    private static void closeQuietly(DataSource dataSource) {
        if (dataSource instanceof HikariDataSource hikari) {
            try {
                hikari.close();
            } catch (RuntimeException e) {
                log.warn("Failed to close pool {}", hikari.getPoolName(), e);
            }
        }
    }
}
