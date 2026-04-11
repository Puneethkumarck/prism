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

import io.helidon.webserver.WebServer;

class CorsConfigurationIntegrationTest {

    static WebServer server;
    static HttpClient client;

    @BeforeAll
    static void startServer() {
        var healthRoutes = new HealthRoutes(Instant.now().getEpochSecond());
        server = WebServer.builder()
                .port(0)
                .routing(r -> r.register(CorsConfiguration.permissive()).register(healthRoutes))
                .build()
                .start();
        client = HttpClient.newHttpClient();
    }

    @AfterAll
    static void stopServer() {
        if (server != null) {
            server.stop();
        }
    }

    @Test
    void shouldAcceptPreflightFromAnyOrigin() throws Exception {
        // given
        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + server.port() + "/health"))
                .method("OPTIONS", HttpRequest.BodyPublishers.noBody())
                .header("Origin", "https://example.com")
                .header("Access-Control-Request-Method", "GET")
                .build();

        // when
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());

        // then
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin"))
                .hasValue("https://example.com");
        assertThat(response.headers().firstValue("Access-Control-Allow-Methods")).isPresent();
    }

    @Test
    void shouldReturnCorsHeadersOnSimpleGet() throws Exception {
        // given
        var request = HttpRequest.newBuilder()
                .uri(URI.create("http://localhost:" + server.port() + "/health"))
                .header("Origin", "https://example.com")
                .GET()
                .build();

        // when
        var response = client.send(request, HttpResponse.BodyHandlers.ofString());

        // then
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Access-Control-Allow-Origin")).hasValue("*");
    }
}
