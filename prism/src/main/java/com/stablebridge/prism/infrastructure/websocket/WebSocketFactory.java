package com.stablebridge.prism.infrastructure.websocket;

import java.net.URI;
import java.net.http.WebSocket;
import java.util.concurrent.CompletableFuture;

@FunctionalInterface
interface WebSocketFactory {

    CompletableFuture<WebSocket> connect(URI endpoint, WebSocket.Listener listener);
}
