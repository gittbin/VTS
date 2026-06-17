// src/main/java/com/project/vts/config/WebSocketConfig.java
package com.project.vts.config;

import com.project.vts.signaling.JwtHandshakeInterceptor;
import com.project.vts.signaling.SignalingHandler;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.socket.config.annotation.EnableWebSocket;
import org.springframework.web.socket.config.annotation.WebSocketConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketHandlerRegistry;

@Configuration
@EnableWebSocket
public class WebSocketConfig implements WebSocketConfigurer {

    private final SignalingHandler signalingHandler;
    private final JwtHandshakeInterceptor jwtHandshakeInterceptor;

    public WebSocketConfig(SignalingHandler signalingHandler, JwtHandshakeInterceptor jwtHandshakeInterceptor) {
        this.signalingHandler = signalingHandler;
        this.jwtHandshakeInterceptor = jwtHandshakeInterceptor;
    }

    @Override
    public void registerWebSocketHandlers(WebSocketHandlerRegistry registry) {
        registry.addHandler(signalingHandler, "/ws")
                .addInterceptors(jwtHandshakeInterceptor)
                .setAllowedOriginPatterns("*");   // cho phép test giữa các mạng/origin khác nhau; handshake vẫn cần token hợp lệ
    }
}