package com.ronkadosh.bubbleup.common.websocket;

import com.ronkadosh.bubbleup.support.IntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.messaging.converter.MappingJackson2MessageConverter;
import org.springframework.messaging.simp.stomp.StompHeaders;
import org.springframework.messaging.simp.stomp.StompSession;
import org.springframework.messaging.simp.stomp.StompSessionHandlerAdapter;
import org.springframework.web.socket.WebSocketHttpHeaders;
import org.springframework.web.socket.client.standard.StandardWebSocketClient;
import org.springframework.web.socket.messaging.WebSocketStompClient;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

import static org.junit.jupiter.api.Assertions.*;

class WebSocketAuthIT extends IntegrationTest {

    @LocalServerPort int port;

    @Test
    void connect_with_valid_jwt_succeeds() throws Exception {
        AuthedUser u = registerAndLogin();
        WebSocketStompClient stomp = newClient();

        StompHeaders connect = new StompHeaders();
        connect.add("Authorization", "Bearer " + u.jwt());

        CompletableFuture<StompSession> future = stomp.connectAsync(
                "ws://localhost:" + port + "/ws",
                new WebSocketHttpHeaders(),
                connect,
                new StompSessionHandlerAdapter() {}
        );

        StompSession session = future.get(5, TimeUnit.SECONDS);
        assertTrue(session.isConnected());
        session.disconnect();
    }

    @Test
    void connect_without_auth_header_is_rejected() throws Exception {
        WebSocketStompClient stomp = newClient();

        CompletableFuture<StompSession> future = stomp.connectAsync(
                "ws://localhost:" + port + "/ws",
                new WebSocketHttpHeaders(),
                new StompHeaders(),
                new StompSessionHandlerAdapter() {}
        );

        assertThrows(Exception.class, () -> {
            try {
                future.get(5, TimeUnit.SECONDS);
            } catch (ExecutionException | TimeoutException e) {
                throw e;
            }
        });
    }

    private static WebSocketStompClient newClient() {
        WebSocketStompClient stomp = new WebSocketStompClient(new StandardWebSocketClient());
        stomp.setMessageConverter(new MappingJackson2MessageConverter());
        return stomp;
    }
}
