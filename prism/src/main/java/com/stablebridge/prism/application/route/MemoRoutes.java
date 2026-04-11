package com.stablebridge.prism.application.route;

import com.stablebridge.prism.api.MemoResponse;
import com.stablebridge.prism.api.Page;
import com.stablebridge.prism.application.mapper.MemoResponseMapper;
import com.stablebridge.prism.domain.port.MemoRepository;

import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class MemoRoutes implements HttpService {

    private static final long DEFAULT_LIMIT = 50L;
    private static final long DEFAULT_OFFSET = 0L;

    private final MemoRepository memoRepository;
    private final MemoResponseMapper mapper;

    @Override
    public void routing(HttpRules rules) {
        rules.get("/api/memos", this::listMemos);
    }

    Page<MemoResponse> listMemos(long limit, long offset) {
        var clampedLimit = TransactionRoutes.clampLimit(limit);
        var data = memoRepository.findAll(clampedLimit, offset).stream()
                .map(mapper::toResponse)
                .toList();
        var total = memoRepository.countAll();
        return Page.<MemoResponse>builder()
                .data(data)
                .total(total)
                .limit(clampedLimit)
                .offset(offset)
                .build();
    }

    private void listMemos(ServerRequest req, ServerResponse res) {
        var limit = req.query().first("limit").map(Long::parseLong).orElse(DEFAULT_LIMIT);
        var offset = req.query().first("offset").map(Long::parseLong).orElse(DEFAULT_OFFSET);
        res.send(listMemos(limit, offset));
    }
}
