package com.stablebridge.prism.application.route;

import java.math.BigDecimal;

import com.stablebridge.prism.api.Page;
import com.stablebridge.prism.api.TransferResponse;
import com.stablebridge.prism.application.mapper.TransferResponseMapper;
import com.stablebridge.prism.domain.port.TransferRepository;

import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class TransferRoutes implements HttpService {

    private final TransferRepository transferRepository;
    private final TransferResponseMapper mapper;

    @Override
    public void routing(HttpRules rules) {
        rules.get("/api/transfers", this::listTransfers);
    }

    Page<TransferResponse> listTransfers(long limit, long offset, BigDecimal minAmount) {
        var clampedLimit = PaginationLimits.clampLimit(limit);
        var data = transferRepository.findByMinAmount(minAmount, clampedLimit, offset).stream()
                .map(mapper::toResponse)
                .toList();
        var total = transferRepository.countByMinAmount(minAmount);
        return Page.<TransferResponse>builder()
                .data(data)
                .total(total)
                .limit(clampedLimit)
                .offset(offset)
                .build();
    }

    private void listTransfers(ServerRequest req, ServerResponse res) {
        var limit = req.query().first("limit").map(Long::parseLong).orElse(PaginationLimits.DEFAULT_LIMIT);
        var offset = req.query().first("offset").map(Long::parseLong).orElse(PaginationLimits.DEFAULT_OFFSET);
        var minAmount = req.query().first("min_amount").map(BigDecimal::new).orElse(BigDecimal.ZERO);
        res.send(listTransfers(limit, offset, minAmount));
    }
}
