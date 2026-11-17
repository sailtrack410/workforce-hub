package com.workforcehub.im.websocket;

import org.springframework.context.annotation.Configuration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

/**
 * 员工端 IM WebSocket 配置。
 */
@Configuration
@EnableWebSocket
public class ImWebSocketConfig implements WebSocketConfigurer {

    private final ImWebSocketHandler imWebSocketHandler;
    private final ImWebSocketHandshakeInterceptor handshakeInterceptor;
    private final String[] allowedOriginPatterns;

    public ImWebSocketConfig(ImWebSocketHandler imWebSocketHandler,
                             ImWebSocketHandshakeInterceptor handshakeInterceptor,
                             @Value("${workforce.im.websocket.allowed-origin-patterns:http://localhost:5173,http://127.0.0.1:5173,http://localhost:5175,http://127.0.0.1:5175}") String[] allowedOriginPatterns) {
        this.imWebSocketHandler = imWebSocketHandler;
        this.handshakeInterceptor = handshakeInterceptor;
        this.allowedOriginPatterns = allowedOriginPatterns;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(imWebSocketHandler, "/ws/employee/im")
            .addInterceptors(handshakeInterceptor)
            .setAllowedOriginPatterns(allowedOriginPatterns);
    }
}
