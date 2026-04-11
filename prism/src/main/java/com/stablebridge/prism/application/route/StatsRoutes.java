package com.stablebridge.prism.application.route;

import com.stablebridge.prism.api.StatsResponse;
import com.stablebridge.prism.application.mapper.StatsResponseMapper;
import com.stablebridge.prism.domain.port.StatsRepository;

import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class StatsRoutes implements HttpService {

    private final StatsRepository statsRepository;
    private final StatsResponseMapper mapper;

    @Override
    public void routing(HttpRules rules) {
        rules.get("/api/stats", this::handle);
    }

    StatsResponse currentStats() {
        return mapper.toResponse(statsRepository.getStats());
    }

    private void handle(ServerRequest req, ServerResponse res) {
        res.send(currentStats());
    }
}
