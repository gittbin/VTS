// src/main/java/com/project/vts/signaling/SignalingHandler.java
package com.project.vts.signaling;

import com.project.vts.service.CallHistoryService;
import com.project.vts.service.FriendshipService;
import tools.jackson.databind.ObjectMapper;
import com.project.vts.repository.UserRepository;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.CloseStatus;
import org.springframework.web.socket.TextMessage;
import org.springframework.web.socket.WebSocketSession;
import org.springframework.web.socket.handler.TextWebSocketHandler;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class SignalingHandler extends TextWebSocketHandler {

    // Các loại bản tin do SERVER phát — client không được giả mạo để relay.
    private static final Set<String> RESERVED =
            Set.of("presence", "presence-snapshot", "pong", "peer-unavailable", "error");

    private final SessionRegistry registry;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper;
    private final CallHistoryService callHistoryService;
    private final FriendshipService friendshipService;

    // Cặp (pairKey) đang trong cuộc gọi → cho relay tiếp dù có unfriend giữa chừng (THIET-KE §6.2, phương án B).
    private final Set<String> activeCallPairs = ConcurrentHashMap.newKeySet();

    // ----- Gọi nhóm (full-mesh, tối đa MAX_GROUP người/phòng) -----
    private static final int MAX_GROUP = 4;
    private final Map<String, Set<String>> rooms = new ConcurrentHashMap<>();   // roomId -> userId thành viên
    private final Map<String, String> userRoom = new ConcurrentHashMap<>();     // userId -> roomId đang ở

    public SignalingHandler(SessionRegistry registry, UserRepository userRepository,
                            ObjectMapper objectMapper, CallHistoryService callHistoryService,
                            FriendshipService friendshipService) {
        this.registry = registry;
        this.userRepository = userRepository;
        this.objectMapper = objectMapper;
        this.callHistoryService = callHistoryService;
        this.friendshipService = friendshipService;
    }

    @Override
    public void afterConnectionEstablished(@NonNull WebSocketSession session) {
        String userId = userId(session);

        registry.register(userId, session);
        updatePresence(userId, true);

        // 1) Gửi cho người vừa vào: BẠN BÈ nào đang online (không lộ người lạ).
        sendJson(session, Map.of(
                "type", "presence-snapshot",
                "online", onlineFriendIds(userId)
        ));
        // 2) Báo cho BẠN BÈ đang online: user này vừa online.
        sendPresenceToFriends(userId, true);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected void handleTextMessage(@NonNull WebSocketSession session, @NonNull TextMessage message) {
        try {
            Map<String, Object> msg = objectMapper.readValue(message.getPayload(), Map.class);
            String fromUserId = userId(session);
            Object type = msg.get("type");

            if ("ping".equals(type)) { sendJson(session, Map.of("type", "pong")); return; }

            // Client xin đồng bộ lại presence bạn bè (vd sau khi danh sách bạn vừa đổi).
            if ("get-presence".equals(type)) {
                sendJson(session, Map.of("type", "presence-snapshot", "online", onlineFriendIds(fromUserId)));
                return;
            }

            if (fromUserId == null) return;

            // ----- Bản tin điều khiển gọi nhóm -----
            if (type instanceof String gt) {
                switch (gt) {
                    case "group-invite" -> { handleGroupInvite(session, fromUserId, msg); return; }
                    case "group-join"   -> { handleGroupJoin(session, fromUserId, msg); return; }
                    case "group-leave"  -> { leaveRoom(fromUserId, str(msg.get("roomId"))); return; }
                    default -> { }
                }
            }

            Object to = msg.get("to");
            if (to instanceof String targetUserId) {
                if (type instanceof String rt && RESERVED.contains(rt)) return;   // chặn giả mạo bản tin hệ thống
                String t = (type instanceof String s) ? s : null;

                // ----- Phân quyền: chỉ bạn bè mới gọi/relay (trừ bản tin thuộc cuộc gọi đang diễn ra) -----
                String pair = FriendshipService.pairKey(fromUserId, targetUserId);
                boolean active = activeCallPairs.contains(pair);   // kiểm tra rẻ (RAM) trước khi hỏi DB
                if ("call-request".equals(t)) {
                    if (!friendshipService.areFriends(fromUserId, targetUserId)) {
                        sendJson(session, Map.of("type", "peer-unavailable", "to", targetUserId));
                        return;                                    // không phải bạn bè → không cho gọi
                    }
                } else if (!active && !inSameRoom(fromUserId, targetUserId)
                        && !friendshipService.areFriends(fromUserId, targetUserId)) {
                    return;                          // người lạ, không cùng phòng & không trong cuộc gọi → bỏ qua
                }

                msg.put("from", fromUserId);
                WebSocketSession target = registry.get(targetUserId);
                boolean delivered = target != null && target.isOpen();

                // Ghi lịch sử theo bản tin điều khiển cuộc gọi + quản lý cặp đang gọi
                if (t != null) {
                    String callId = (msg.get("callId") instanceof String c) ? c : null;
                    switch (t) {
                        case "call-request" -> {
                            String ct = (msg.get("callType") instanceof String x) ? x : "video";
                            callHistoryService.onRequest(callId, fromUserId, targetUserId, ct);
                            if (delivered) activeCallPairs.add(pair);   // cuộc gọi bắt đầu
                            else callHistoryService.onCancel(callId);   // không tới được → nhỡ
                        }
                        case "call-accept" -> callHistoryService.onAccept(callId);
                        case "call-reject" -> { callHistoryService.onReject(callId); activeCallPairs.remove(pair); }
                        case "call-cancel" -> { callHistoryService.onCancel(callId); activeCallPairs.remove(pair); }
                        case "call-end"    -> { callHistoryService.onEnd(callId);    activeCallPairs.remove(pair); }
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
            removeActivePairsOf(userId);                         // gỡ các cặp đang gọi liên quan
            leaveRoom(userId, userRoom.get(userId));            // rời phòng nhóm (nếu đang ở) + báo người còn lại
            updatePresence(userId, false);
            sendPresenceToFriends(userId, false);                // báo offline chỉ cho bạn bè
        }
    }

    // ----- helpers -----

    private String userId(WebSocketSession session) {
        Object v = session.getAttributes().get("userId");
        return v == null ? null : v.toString();
    }

    /** Bạn bè đang có session mở. */
    private List<String> onlineFriendIds(String userId) {
        return friendshipService.friendIds(userId).stream()
                .filter(registry::isOnline)
                .toList();
    }

    /** Gửi presence (online/offline) CHỈ tới bạn bè đang online. */
    private void sendPresenceToFriends(String userId, boolean online) {
        if (userId == null) return;
        Map<String, Object> payload = Map.of("type", "presence", "userId", userId, "online", online);
        for (String friendId : friendshipService.friendIds(userId)) {
            WebSocketSession s = registry.get(friendId);
            if (s != null && s.isOpen()) sendJson(s, payload);
        }
    }

    /** Gỡ mọi cặp đang gọi có chứa userId (khi user rớt kết nối). userId là hex Mongo, không chứa '_'. */
    private void removeActivePairsOf(String userId) {
        activeCallPairs.removeIf(pk -> pk.startsWith(userId + "_") || pk.endsWith("_" + userId));
    }

    // ----- Gọi nhóm -----

    /** Host mời 1 người vào phòng. Host tự vào phòng ở lần mời đầu; chỉ mời được bạn bè; tôn trọng giới hạn. */
    private void handleGroupInvite(WebSocketSession session, String fromUserId, Map<String, Object> msg) {
        String roomId = str(msg.get("roomId"));
        String to = str(msg.get("to"));
        if (roomId == null || to == null) return;
        if (!friendshipService.areFriends(fromUserId, to)) {
            sendJson(session, Map.of("type", "peer-unavailable", "to", to));
            return;
        }
        Set<String> room = rooms.computeIfAbsent(roomId, k -> ConcurrentHashMap.newKeySet());
        synchronized (room) {
            if (!room.contains(fromUserId)) {
                if (room.size() >= MAX_GROUP) { sendJson(session, Map.of("type", "group-full", "roomId", roomId)); return; }
                room.add(fromUserId);
                userRoom.put(fromUserId, roomId);
            }
            if (room.size() >= MAX_GROUP) { sendJson(session, Map.of("type", "group-full", "roomId", roomId)); return; }
        }

        WebSocketSession target = registry.get(to);
        if (target != null && target.isOpen()) {
            String fromName = str(msg.get("fromName"));
            String callType = "audio".equals(str(msg.get("callType"))) ? "audio" : "video";
            sendJson(target, Map.of(
                    "type", "group-invite", "roomId", roomId,
                    "from", fromUserId,
                    "fromName", fromName != null ? fromName : displayName(fromUserId),
                    "callType", callType));
        } else {
            sendJson(session, Map.of("type", "peer-unavailable", "to", to));
        }
    }

    /** Người được mời chấp nhận: thêm vào phòng, gửi danh sách thành viên cũ (để CHỜ offer),
     *  báo thành viên cũ tạo offer tới người mới (quy ước "người cũ offer người mới" → tránh glare). */
    private void handleGroupJoin(WebSocketSession session, String fromUserId, Map<String, Object> msg) {
        String roomId = str(msg.get("roomId"));
        if (roomId == null) return;
        Set<String> room = rooms.get(roomId);
        if (room == null) { sendJson(session, Map.of("type", "group-closed", "roomId", roomId)); return; }
        // Khoá theo phòng → tránh race khi nhiều người join cùng lúc (đảm bảo mesh đủ cạnh).
        synchronized (room) {
            if (room.contains(fromUserId)) return;
            if (room.size() >= MAX_GROUP) { sendJson(session, Map.of("type", "group-full", "roomId", roomId)); return; }

            List<Map<String, Object>> peers = new ArrayList<>();
            for (String uid : room) {
                if (registry.isOnline(uid)) peers.add(Map.of("id", uid, "name", displayName(uid)));
            }
            room.add(fromUserId);
            userRoom.put(fromUserId, roomId);

            sendJson(session, Map.of("type", "group-joined", "roomId", roomId, "peers", peers));
            String newName = displayName(fromUserId);
            for (Map<String, Object> p : peers) {
                WebSocketSession s = registry.get((String) p.get("id"));
                if (s != null && s.isOpen())
                    sendJson(s, Map.of("type", "group-peer-joined", "roomId", roomId,
                            "peerId", fromUserId, "peerName", newName));
            }
        }
    }

    /** Rời phòng + báo những người còn lại đóng kết nối với mình; phòng trống thì xoá. */
    private void leaveRoom(String userId, String roomId) {
        if (userId == null) return;
        if (roomId == null) roomId = userRoom.get(userId);
        if (roomId == null) return;
        userRoom.remove(userId, roomId);
        Set<String> room = rooms.get(roomId);
        if (room == null) return;
        synchronized (room) {
            room.remove(userId);
            for (String uid : room) {
                WebSocketSession s = registry.get(uid);
                if (s != null && s.isOpen())
                    sendJson(s, Map.of("type", "group-peer-left", "roomId", roomId, "peerId", userId));
            }
            if (room.isEmpty()) rooms.remove(roomId);
        }
    }

    private boolean inSameRoom(String a, String b) {
        String ra = userRoom.get(a), rb = userRoom.get(b);
        return ra != null && ra.equals(rb);
    }

    private String displayName(String userId) {
        if (userId == null) return "Người dùng";
        try {
            return userRepository.findById(userId)
                    .map(u -> u.getDisplayName() != null && !u.getDisplayName().isBlank()
                            ? u.getDisplayName() : u.getUsername())
                    .orElse("Người dùng");
        } catch (Exception e) { return "Người dùng"; }
    }

    private static String str(Object o) { return (o instanceof String s) ? s : null; }

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
