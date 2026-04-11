package com.stablebridge.prism.application.route;

import java.util.NoSuchElementException;

import com.stablebridge.prism.api.AccountResponse;
import com.stablebridge.prism.application.mapper.AccountResponseMapper;
import com.stablebridge.prism.domain.model.Pubkey;
import com.stablebridge.prism.domain.port.AccountRepository;

import io.helidon.webserver.http.HttpRules;
import io.helidon.webserver.http.HttpService;
import io.helidon.webserver.http.ServerRequest;
import io.helidon.webserver.http.ServerResponse;
import lombok.RequiredArgsConstructor;

@RequiredArgsConstructor
public class AccountRoutes implements HttpService {

    private final AccountRepository accountRepository;
    private final AccountResponseMapper mapper;

    @Override
    public void routing(HttpRules rules) {
        rules.get("/api/accounts/{pubkey}", this::getByPubkey);
    }

    AccountResponse getByPubkey(String pubkeyValue) {
        var pubkey = new Pubkey(pubkeyValue);
        return accountRepository.findByPubkey(pubkey)
                .map(mapper::toResponse)
                .orElseThrow(() -> new NoSuchElementException("Account not found: " + pubkeyValue));
    }

    private void getByPubkey(ServerRequest req, ServerResponse res) {
        var pubkeyValue = req.path().pathParameters().get("pubkey");
        res.send(getByPubkey(pubkeyValue));
    }
}
