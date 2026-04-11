package com.stablebridge.prism.application.route;

import java.time.Instant;

import com.stablebridge.prism.api.HealthResponse;

import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class HealthRoutes implements HttpService {

    private final long startEpochSecs;

    @Override
    public void routing(HttpRules rules) {
        rules.get("/health", this::handle);
    }

    HealthResponse currentHealth(long nowEpochSecs) {
        return HealthResponse.builder()
                .status("ok")
                .uptimeSecs(nowEpochSecs - startEpochSecs)
                .build();
    }

    private void handle(ServerRequest req, ServerResponse res) {
        res.send(currentHealth(Instant.now().getEpochSecond()));
    }
}
