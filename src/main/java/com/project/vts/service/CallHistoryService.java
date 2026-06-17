// src/main/java/com/project/vts/service/CallHistoryService.java
package com.project.vts.service;

import com.project.vts.dto.response.CallHistoryResponse;
import com.project.vts.model.CallHistory;
import com.project.vts.model.User;
import com.project.vts.repository.CallHistoryRepository;
import com.project.vts.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

@Service
public class CallHistoryService {

    public static final String RINGING = "RINGING", ONGOING = "ONGOING",
            COMPLETED = "COMPLETED", REJECTED = "REJECTED", MISSED = "MISSED";

    private final CallHistoryRepository repo;
    private final UserRepository userRepository;

    public CallHistoryService(CallHistoryRepository repo, UserRepository userRepository) {
        this.repo = repo;
        this.userRepository = userRepository;
    }

    public void onRequest(String callId, String callerId, String calleeId, String type) {
        if (callId == null) return;
        User caller = userRepository.findById(callerId).orElse(null);
        User callee = userRepository.findById(calleeId).orElse(null);
        repo.save(CallHistory.builder()
                .id(callId)
                .callerId(callerId).calleeId(calleeId)
                .callerUsername(caller != null ? caller.getUsername() : null)
                .calleeUsername(callee != null ? callee.getUsername() : null)
                .callerName(caller != null ? caller.getDisplayName() : null)
                .calleeName(callee != null ? callee.getDisplayName() : null)
                .type("audio".equalsIgnoreCase(type) ? "AUDIO" : "VIDEO")
                .status(RINGING)
                .createdAt(Instant.now())
                .durationSeconds(0)
                .build());
    }

    public void onAccept(String callId) {
        update(callId, h -> { if (RINGING.equals(h.getStatus())) { h.setStatus(ONGOING); h.setStartedAt(Instant.now()); } });
    }
    public void onReject(String callId) {
        update(callId, h -> { if (isOpen(h)) { h.setStatus(REJECTED); finish(h); } });
    }
    public void onCancel(String callId) {
        update(callId, h -> { if (RINGING.equals(h.getStatus())) { h.setStatus(MISSED); finish(h); } });
    }
    public void onEnd(String callId) {
        update(callId, h -> {
            if (!isOpen(h)) return;
            h.setStatus(ONGOING.equals(h.getStatus()) ? COMPLETED : MISSED);
            finish(h);
        });
    }

    /** Dọn cuộc gọi đang dở khi user rớt kết nối. */
    public void finalizeActiveForUser(String userId) {
        for (CallHistory h : repo.findActiveForUser(userId, List.of(RINGING, ONGOING))) {
            h.setStatus(ONGOING.equals(h.getStatus()) ? COMPLETED : MISSED);
            finish(h);
            repo.save(h);
        }
    }

    public List<CallHistoryResponse> historyForUsername(String username) {
        return repo.findTop50ByCallerUsernameOrCalleeUsernameOrderByCreatedAtDesc(username, username)
                .stream().map(h -> toResponse(h, username)).toList();
    }

    private void finish(CallHistory h) {
        h.setEndedAt(Instant.now());
        if (h.getStartedAt() != null)
            h.setDurationSeconds(Math.max(0, Duration.between(h.getStartedAt(), h.getEndedAt()).getSeconds()));
    }
    private boolean isOpen(CallHistory h) { return RINGING.equals(h.getStatus()) || ONGOING.equals(h.getStatus()); }
    private void update(String callId, Consumer<CallHistory> fn) {
        if (callId == null) return;
        repo.findById(callId).ifPresent(h -> { fn.accept(h); repo.save(h); });
    }

    private CallHistoryResponse toResponse(CallHistory h, String me) {
        boolean outgoing = me.equals(h.getCallerUsername());
        return new CallHistoryResponse(
                h.getId(), h.getType(), h.getStatus(),
                outgoing ? "outgoing" : "incoming",
                outgoing ? h.getCalleeId() : h.getCallerId(),
                outgoing ? h.getCalleeUsername() : h.getCallerUsername(),
                outgoing ? h.getCalleeName() : h.getCallerName(),
                h.getCreatedAt(), h.getStartedAt(), h.getEndedAt(), h.getDurationSeconds());
    }
}