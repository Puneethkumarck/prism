package com.stablebridge.prism.application;

import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.function.Consumer;
import javax.sql.DataSource;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.stablebridge.prism.api.HealthResponse;
import com.stablebridge.prism.application.config.IndexerConfig;
import com.stablebridge.prism.domain.model.Account;
import com.stablebridge.prism.domain.model.SolanaTransaction;
import com.stablebridge.prism.domain.port.TransactionStream;
import com.stablebridge.prism.testutil.SharedPostgresContainer;

class IndexerApplicationIntegrationTest {

    static DataSource writePool;
    static DataSource readPool;
    static IndexerLifecycle lifecycle;
    static RecordingTransactionStream stream;
    static HttpClient client;
    static ObjectMapper objectMapper;

    @BeforeAll
    static void startApp() {
        writePool = SharedPostgresContainer.writePool();
        readPool = SharedPostgresContainer.readPool();
        stream = new RecordingTransactionStream();
        var config = IndexerConfig.builder()
                .streamMode("websocket")
                .rpcWsEndpoint("wss://localhost:0")
                .databaseUrl("jdbc:postgresql://unused")
                .benchLog("build/indexer-integration-benchmark.log")
                .consoleLog(false)
                .apiPort(0)
                .build();
        lifecycle = IndexerApplication.start(config, writePool, readPool, stream);
        client = HttpClient.newHttpClient();
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @AfterAll
    static void stopApp() {
        if (lifecycle != null) {
            lifecycle.stop();
        }
    }

    @Test
    void shouldStartAndSubscribeToStream() {
        // given
        var startedLifecycle = lifecycle;

        // when
        var running = startedLifecycle.isRunning();

        // then
        assertThat(running).isTrue();
        assertThat(stream.subscribed).isTrue();
    }

    @Test
    void shouldRespondToHealth() throws Exception {
        // given
        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + lifecycle.port() + "/health"))
                .GET()
                .build();

        // when
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());

        // then
        assertThat(response.statusCode()).isEqualTo(200);
        var body = objectMapper.readValue(response.body(), HealthResponse.class);
        var expected = HealthResponse.builder().status("ok").build();
        assertThat(body)
                .usingRecursiveComparison()
                .ignoringFields("uptimeSecs")
                .isEqualTo(expected);
    }

    @Test
    void shouldExposePrometheusMetrics() throws Exception {
        // given
        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + lifecycle.port() + "/metrics"))
                .GET()
                .build();

        // when
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());

        // then
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body())
                .contains("indexer_tx_received")
                .contains("indexer_batches")
                .contains("# HELP")
                .contains("# TYPE");
    }

    @Test
    void shouldShutdownGracefullyWithinTimeout() {
        // given
        var shutdownStream = new RecordingTransactionStream();
        var config = IndexerConfig.builder()
                .streamMode("websocket")
                .rpcWsEndpoint("wss://localhost:0")
                .databaseUrl("jdbc:postgresql://unused")
                .benchLog("build/indexer-shutdown-benchmark.log")
                .consoleLog(false)
                .apiPort(0)
                .build();
        var shutdownLifecycle = IndexerApplication.start(config, writePool, readPool, shutdownStream);

        // when
        shutdownLifecycle.stop();

        // then
        await().atMost(10, SECONDS)
                .untilAsserted(() -> assertThat(shutdownLifecycle.isRunning()).isFalse());
        assertThat(shutdownStream.closed).isTrue();
    }

    static final class RecordingTransactionStream implements TransactionStream {
        volatile boolean subscribed;
        volatile boolean closed;

        @Override
        public void subscribe(Consumer<SolanaTransaction> txConsumer, Consumer<Account> acctConsumer) {
            subscribed = true;
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
