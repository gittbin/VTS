// src/main/java/com/project/vts/dto/response/CallHistoryResponse.java
package com.project.vts.dto.response;

import java.time.Instant;

public record CallHistoryResponse(
        String id,
        String type,
        String status,
        String direction,        // "incoming" | "outgoing" (so với người đang xem)
        String peerId,
        String peerUsername,
        String peerName,
        Instant createdAt,
        Instant startedAt,
        Instant endedAt,
        long durationSeconds
) {}