package com.stablebridge.prism.infrastructure.websocket;

import static java.util.concurrent.TimeUnit.MILLISECONDS;
import static java.util.concurrent.TimeUnit.SECONDS;
import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;

import java.net.http.WebSocket;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.stablebridge.prism.domain.model.Account;
import com.stablebridge.prism.domain.model.SolanaTransaction;
import com.stablebridge.prism.infrastructure.grpc.ReconnectHandler;

class WebSocketTransactionStreamTest {

    private static final String SOME_WS_URL = "wss://example.test/blocks";

    private static final String EXPECTED_SUBSCRIBE_PAYLOAD =
            "{\"jsonrpc\":\"2.0\",\"id\":1,\"method\":\"blockSubscribe\",\"params\":"
                    + "[\"all\",{\"commitment\":\"confirmed\",\"encoding\":\"jsonParsed\","
                    + "\"transactionDetails\":\"full\",\"maxSupportedTransactionVersion\":0}]}";

    private static final String SOME_SIGNATURE_BASE58 = "5Kx7aEwMbWSSig00000000000000000000000000";
    private static final String SOME_SENDER_PUBKEY_BASE58 = "SenderPubkey1234567890abcdefghij12345678";
    private static final String SOME_RECEIVER_PUBKEY_BASE58 = "ReceiverPubkey1234567890abcdefghij123456";
    private static final String SOME_VOTE_AUTHORITY_BASE58 = "VoteAuthority1234567890abcdefghij1234567";

    private BlockNotificationParser parser;
    private ReconnectHandler reconnectHandler;
    private ObjectMapper objectMapper;
    private WebSocket webSocket;
    private CopyOnWriteArrayList<WebSocket.Listener> listeners;
    private WebSocketFactory factory;
    private WebSocketTransactionStream stream;
    private CopyOnWriteArrayList<SolanaTransaction> txCaptor;
    private CopyOnWriteArrayList<Account> acctCaptor;

    @BeforeEach
    void setUp() {
        parser = new BlockNotificationParser();
        reconnectHandler = spy(new ReconnectHandler());
        given(reconnectHandler.nextDelay()).willReturn(0L);
        objectMapper = new ObjectMapper();
        webSocket = mock(WebSocket.class);
        listeners = new CopyOnWriteArrayList<>();
        factory = (uri, listener) -> {
            listeners.add(listener);
            return CompletableFuture.completedFuture(webSocket);
        };
        stream = new WebSocketTransactionStream(
                SOME_WS_URL, parser, reconnectHandler, factory, objectMapper, Instant::now);
        txCaptor = new CopyOnWriteArrayList<>();
        acctCaptor = new CopyOnWriteArrayList<>();
    }

    @AfterEach
    void tearDown() {
        if (stream != null) {
            stream.close();
        }
    }

    @Test
    void shouldSendBlockSubscribeOnConnect() {
        // given
        // setup handled in @BeforeEach

        // when
        stream.subscribe(txCaptor::add, acctCaptor::add);

        // then
        await().atMost(5, SECONDS)
                .untilAsserted(() -> then(webSocket).should().sendText(EXPECTED_SUBSCRIBE_PAYLOAD, true));
    }

    @Test
    void shouldFilterVoteTransactions() {
        // given
        stream.subscribe(txCaptor::add, acctCaptor::add);
        await().atMost(5, SECONDS).until(() -> !listeners.isEmpty());
        var listener = listeners.get(0);
        var frame = blockFrame(
                """
                {
                  "slot": 280000000,
                  "transactions": [
                    {
                      "transaction": {
                        "signatures": ["%s"],
                        "message": {
                          "accountKeys": [
                            {"pubkey": "%s"},
                            {"pubkey": "Vote111111111111111111111111111111111111111"}
                          ],
                          "instructions": [
                            {"programId": "Vote111111111111111111111111111111111111111", "parsed": "vote"}
                          ]
                        }
                      },
                      "meta": {"err": null, "preBalances": [1000000000, 0], "postBalances": [999995000, 0]}
                    }
                  ]
                }
                """
                        .formatted(SOME_SIGNATURE_BASE58, SOME_VOTE_AUTHORITY_BASE58));

        // when
        listener.onText(webSocket, frame, true);

        // then
        await().during(500, MILLISECONDS).atMost(2, SECONDS).untilAsserted(() -> {
            assertThat(txCaptor).isEmpty();
            assertThat(acctCaptor).isEmpty();
        });
    }

    @Test
    void shouldRouteNonVoteTransactions() throws Exception {
        // given
        stream.subscribe(txCaptor::add, acctCaptor::add);
        await().atMost(5, SECONDS).until(() -> !listeners.isEmpty());
        var listener = listeners.get(0);
        var blockValueJson =
                """
                {
                  "slot": 280000000,
                  "transactions": [
                    {
                      "transaction": {
                        "signatures": ["%s"],
                        "message": {
                          "accountKeys": [
                            {"pubkey": "%s"},
                            {"pubkey": "%s"}
                          ],
                          "instructions": []
                        }
                      },
                      "meta": {
                        "err": null,
                        "fee": 5000,
                        "preBalances": [5000000000, 0],
                        "postBalances": [0, 5000000000]
                      }
                    }
                  ]
                }
                """
                        .formatted(SOME_SIGNATURE_BASE58, SOME_SENDER_PUBKEY_BASE58, SOME_RECEIVER_PUBKEY_BASE58);
        var expected = new Routed(
                parser.parseBlock(objectMapper.readTree(blockValueJson)),
                parser.extractFeePayers(objectMapper.readTree(blockValueJson)));
        var frame = blockFrame(blockValueJson);

        // when
        listener.onText(webSocket, frame, true);

        // then
        await().atMost(5, SECONDS)
                .untilAsserted(() -> assertThat(new Routed(List.copyOf(txCaptor), List.copyOf(acctCaptor)))
                        .usingRecursiveComparison()
                        .isEqualTo(expected));
    }

    @Test
    void shouldFilterVoteTransactionsResolvedViaProgramIdIndex() {
        // given
        stream.subscribe(txCaptor::add, acctCaptor::add);
        await().atMost(5, SECONDS).until(() -> !listeners.isEmpty());
        var listener = listeners.get(0);
        var frame = blockFrame(
                """
                {
                  "slot": 280000000,
                  "transactions": [
                    {
                      "transaction": {
                        "signatures": ["%s"],
                        "message": {
                          "accountKeys": [
                            {"pubkey": "%s"},
                            {"pubkey": "Vote111111111111111111111111111111111111111"}
                          ],
                          "instructions": [
                            {"programIdIndex": 1, "accounts": [0], "data": ""}
                          ]
                        }
                      },
                      "meta": {"err": null, "preBalances": [1000000000, 0], "postBalances": [999995000, 0]}
                    }
                  ]
                }
                """
                        .formatted(SOME_SIGNATURE_BASE58, SOME_VOTE_AUTHORITY_BASE58));

        // when
        listener.onText(webSocket, frame, true);

        // then
        await().during(500, MILLISECONDS).atMost(2, SECONDS).untilAsserted(() -> {
            assertThat(txCaptor).isEmpty();
            assertThat(acctCaptor).isEmpty();
        });
    }

    private record Routed(List<SolanaTransaction> txs, List<Account> feePayers) {}

    @Test
    void shouldReconnectOnDisconnect() {
        // given
        stream.subscribe(txCaptor::add, acctCaptor::add);
        await().atMost(5, SECONDS).until(() -> !listeners.isEmpty());
        var firstListener = listeners.get(0);

        // when
        firstListener.onClose(webSocket, WebSocket.NORMAL_CLOSURE, "peer closed");

        // then
        await().atMost(5, SECONDS).untilAsserted(() -> {
            assertThat(listeners).hasSizeGreaterThanOrEqualTo(2);
            then(reconnectHandler).should(atLeastOnce()).nextDelay();
        });
    }

    private static String blockFrame(String blockValueJson) {
        return "{\"jsonrpc\":\"2.0\",\"method\":\"blockNotification\",\"params\":{\"subscription\":0,"
                + "\"result\":{\"context\":{\"slot\":0},\"value\":"
                + blockValueJson
                + "}}}";
    }
}
