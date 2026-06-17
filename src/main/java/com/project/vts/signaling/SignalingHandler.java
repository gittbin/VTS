// src/main/java/com/project/vts/signaling/SignalingHandler.java
package com.project.vts.signaling;

import com.project.vts.service.CallHistoryService;
import tools.jackson.databind.ObjectMapper;
import com.project.vts.repository.UserRepository;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

@Component
public class SignalingHandler extends TextWebSocketHandler {

    // Các loại bản tin do SERVER phát — client không được giả mạo để relay.
    private static final Set<String> RESERVED =
            Set.of("presence", "presence-snapshot", "pong", "peer-unavailable", "error");

    private final SessionRegistry registry;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final CallHistoryService callHistoryService;

    public SignalingHandler(SessionRegistry registry, UserRepository userRepository,
                            ObjectMapper objectMapper, CallHistoryService callHistoryService) {
        this.registry = registry;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.callHistoryService = callHistoryService;
    }

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) {
        String userId = userId(session);

        registry.register(userId, session);
        updatePresence(userId, true);

        // 1) Gửi cho người vừa vào: ai đang online sẵn.
        sendJson(session, Map.of(
                "type", "presence-snapshot",
                "online", registry.onlineUserIds()
        ));
        // 2) Báo cho mọi người khác: user này vừa online.
        broadcastExcept(userId, Map.of(
                "type", "presence",
                "userId", userId,
                "online", true
        ));
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void handleTextMessage(@NonNull WebSocketSession session, @NonNull TextMessage message) {
        try {
            Map<String, Object> msg = objectMapper.readValue(message.getPayload(), Map.class);
            String fromUserId = userId(session);
            Object type = msg.get("type");

            if ("ping".equals(type)) { sendJson(session, Map.of("type", "pong")); return; }

            Object to = msg.get("to");
            if (to instanceof String targetUserId) {
                if (type instanceof String t && RESERVED.contains(t)) return;   // chặn giả mạo bản tin hệ thống
                msg.put("from", fromUserId);

                WebSocketSession target = registry.get(targetUserId);
                boolean delivered = target != null && target.isOpen();

                // Ghi lịch sử theo bản tin điều khiển cuộc gọi
                if (type instanceof String t) {
                    String callId = (msg.get("callId") instanceof String c) ? c : null;
                    switch (t) {
                        case "call-request" -> {
                            String ct = (msg.get("callType") instanceof String x) ? x : "video";
                            callHistoryService.onRequest(callId, fromUserId, targetUserId, ct);
                            if (!delivered) callHistoryService.onCancel(callId);   // không tới được → nhỡ
                        }
                        case "call-accept" -> callHistoryService.onAccept(callId);
                        case "call-reject" -> callHistoryService.onReject(callId);
                        case "call-cancel" -> callHistoryService.onCancel(callId);
                        case "call-end"    -> callHistoryService.onEnd(callId);
                        default -> { }
                    }
                }

                if (delivered) sendJson(target, msg);
                else sendJson(session, Map.of("type", "peer-unavailable", "to", targetUserId));
            }
        } catch (Exception e) {
            sendJson(session, Map.of("type", "error", "message", "Bản tin không hợp lệ"));
        }
    }

    @Override
    public void afterConnectionClosed(@NonNull WebSocketSession session, @NonNull CloseStatus status) {
        String userId = userId(session);
        registry.remove(userId, session);
        if (userId != null && !registry.isOnline(userId)) {
            callHistoryService.finalizeActiveForUser(userId);   // dọn cuộc gọi đang dở khi rớt mạng
            updatePresence(userId, false);
            broadcastExcept(userId, Map.of("type", "presence", "userId", userId, "online", false));
        }
    }

    // ----- helpers -----

    private String userId(WebSocketSession session) {
        Object v = session.getAttributes().get("userId");
        return v == null ? null : v.toString();
    }

    /** Gửi tới mọi session khác (trừ chính user đó). */
    private void broadcastExcept(String exceptUserId, Object payload) {
        for (WebSocketSession s : registry.allSessions()) {
            Object uid = s.getAttributes().get("userId");
            if (uid != null && !uid.equals(exceptUserId)) {
                sendJson(s, payload);
            }
        }
    }

    /** Ghi DB best-effort: online + lastSeen. RAM (registry) mới là nguồn chân lý. */
    private void updatePresence(String userId, boolean online) {
        if (userId == null) return;
        try {
            userRepository.findById(userId).ifPresent(u -> {
                u.setOnline(online);
                u.setLastSeen(Instant.now());
                userRepository.save(u);
            });
        } catch (Exception ignored) {
            // DB lỗi không được làm sập signaling
        }
    }

    /** sendMessage KHÔNG thread-safe → đồng bộ theo từng session. */
    private void sendJson(WebSocketSession session, Object payload) {
        if (session == null || !session.isOpen()) return;
        try {
            String json = objectMapper.writeValueAsString(payload);
            synchronized (session) {
                session.sendMessage(new TextMessage(json));
            }
        } catch (Exception ignored) {
        }
    }
}