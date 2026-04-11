package com.stablebridge.prism.application.route;

import java.util.List;
import java.util.NoSuchElementException;

import com.stablebridge.prism.api.Page;
import com.stablebridge.prism.api.TransactionResponse;
import com.stablebridge.prism.application.mapper.TransactionResponseMapper;
import com.stablebridge.prism.domain.model.Signature;
import com.stablebridge.prism.domain.port.TransactionRepository;

import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class TransactionRoutes implements HttpService {

    private final TransactionRepository transactionRepository;
    private final TransactionResponseMapper mapper;

    @Override
    public void routing(HttpRules rules) {
        rules.get("/api/transactions", this::listTransactions)
                .get("/api/transactions/{signature}", this::getBySignature)
                .get("/api/slots/{slot}", this::getBySlot);
    }

    Page<TransactionResponse> listTransactions(long limit, long offset, Boolean success) {
        var clampedLimit = PaginationLimits.clampLimit(limit);
        var data = transactionRepository.findAll(clampedLimit, offset, success).stream()
                .map(mapper::toResponse)
                .toList();
        var total = success != null
                ? transactionRepository.countBySuccess(success)
                : transactionRepository.countAll();
        return Page.<TransactionResponse>builder()
                .data(data)
                .total(total)
                .limit(clampedLimit)
                .offset(offset)
                .build();
    }

    TransactionResponse getBySignature(String signatureValue) {
        var signature = new Signature(signatureValue);
        return transactionRepository.findBySignature(signature)
                .map(mapper::toResponse)
                .orElseThrow(() -> new NoSuchElementException(
                        "Transaction not found: " + signatureValue));
    }

    List<TransactionResponse> getBySlot(long slot) {
        return transactionRepository.findBySlot(slot).stream()
                .map(mapper::toResponse)
                .toList();
    }

    private void listTransactions(ServerRequest req, ServerResponse res) {
        var limit = req.query().first("limit").map(Long::parseLong).orElse(PaginationLimits.DEFAULT_LIMIT);
        var offset = req.query().first("offset").map(Long::parseLong).orElse(PaginationLimits.DEFAULT_OFFSET);
        var success = req.query().first("success").map(Boolean::parseBoolean).orElse(null);
        res.send(listTransactions(limit, offset, success));
    }

    private void getBySignature(ServerRequest req, ServerResponse res) {
        var signatureValue = req.path().pathParameters().get("signature");
        res.send(getBySignature(signatureValue));
    }

    private void getBySlot(ServerRequest req, ServerResponse res) {
        var slot = Long.parseLong(req.path().pathParameters().get("slot"));
        res.send(getBySlot(slot));
    }
}
