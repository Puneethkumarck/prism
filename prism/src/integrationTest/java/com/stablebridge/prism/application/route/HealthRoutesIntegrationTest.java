package com.stablebridge.prism.application.route;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Instant;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.stablebridge.prism.api.HealthResponse;
import com.stablebridge.prism.application.error.GlobalErrorHandler;

import io.helidon.webserver.WebServer;

class HealthRoutesIntegrationTest {

    static WebServer server;
    static HttpClient client;
    static ObjectMapper objectMapper;

    @BeforeAll
    static void startServer() {
        var routes = new HealthRoutes(Instant.now().getEpochSecond());
        server = WebServer.builder()
                .port(0)
                .routing(r -> {
                    r.register(routes);
                    GlobalErrorHandler.register(r);
                })
                .build()
                .start();
        client = HttpClient.newHttpClient();
        objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void shouldReturnHealthOk() throws Exception {
        // given
        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + server.port() + "/health"))
                .GET()
                .build();

        // when
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());

        // then
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("uptime_secs");
        var body = objectMapper.readValue(response.body(), HealthResponse.class);
        var expected = HealthResponse.builder().status("ok").build();
        assertThat(body)
                .usingRecursiveComparison()
                .ignoringFields("uptimeSecs")
                .isEqualTo(expected);
        assertThat(body.uptimeSecs()).isGreaterThanOrEqualTo(0L);
    }
}
