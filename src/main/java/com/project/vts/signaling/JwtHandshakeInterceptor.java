// src/main/java/com/project/vts/signaling/JwtHandshakeInterceptor.java
package com.project.vts.signaling;

import com.project.vts.security.JwtService;
import org.springframework.http.server.ServerHttpRequest;
import org.springframework.http.server.ServerHttpResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketHandler;
import org.springframework.web.socket.server.HandshakeInterceptor;
import org.springframework.web.util.UriComponentsBuilder;

import java.util.List;
import java.util.Map;

@Component
public class JwtHandshakeInterceptor implements HandshakeInterceptor {

    private final JwtService jwtService;

    public JwtHandshakeInterceptor(JwtService jwtService) {
        this.jwtService = jwtService;
    }

    @Override
    public boolean beforeHandshake(ServerHttpRequest request, ServerHttpResponse response,
                                   WebSocketHandler wsHandler, Map<String, Object> attributes) {
        // Trình duyệt không set được header cho WebSocket → token truyền qua query: /ws?token=...
        List<String> tokens = UriComponentsBuilder.fromUri(request.getURI())
                .build().getQueryParams().get("token");
        String token = (tokens == null || tokens.isEmpty()) ? null : tokens.get(0);

        if (token == null || !jwtService.isValid(token)) {
            return false;   // từ chối handshake → client nhận lỗi kết nối
        }

        // Lưu vào attributes để SignalingHandler đọc lại từ session.
        attributes.put("userId", jwtService.extractUserId(token));
        attributes.put("username", jwtService.extractUsername(token));
        return true;
    }

    @Override
    public void afterHandshake(ServerHttpRequest request, ServerHttpResponse response,
                               WebSocketHandler wsHandler, Exception exception) {
        // không cần làm gì
    }
}