// src/main/java/com/project/vts/signaling/SessionRegistry.java
package com.project.vts.signaling;

import org.springframework.stereotype.Component;
import org.springframework.web.socket.WebSocketSession;

import java.util.Collection;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SessionRegistry {

    // userId -> session đang mở. NGUỒN CHÂN LÝ cho trạng thái online (RAM, không phải DB).
    private final ConcurrentHashMap<String, WebSocketSession> sessions = new ConcurrentHashMap<>();

    public void register(String userId, WebSocketSession session) {
        WebSocketSession old = sessions.put(userId, session);
        // User mở tab/thiết bị mới → đóng kết nối cũ, mỗi user chỉ giữ 1 session.
        if (old != null && old.isOpen() && !old.getId().equals(session.getId())) {
            try { old.close(); } catch (Exception ignored) {}
        }
    }

    /** Gỡ session khi đóng — chỉ gỡ nếu mapping vẫn trỏ đúng session này (tránh xoá nhầm session mới). */
    public void remove(String userId, WebSocketSession session) {
        if (userId != null) sessions.remove(userId, session);
    }

    public WebSocketSession get(String userId) {
        return sessions.get(userId);
    }

    public boolean isOnline(String userId) {
        WebSocketSession s = sessions.get(userId);
        return s != null && s.isOpen();
    }

    public Set<String> onlineUserIds() {
        return Set.copyOf(sessions.keySet());
    }

    public Collection<WebSocketSession> allSessions() {
        return sessions.values();
    }
}