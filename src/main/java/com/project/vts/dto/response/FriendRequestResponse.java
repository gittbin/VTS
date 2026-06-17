// src/main/java/com/project/vts/dto/response/FriendRequestResponse.java
package com.project.vts.dto.response;

import java.time.Instant;

/** Một lời mời kết bạn (đến hoặc đi). "người kia" là đối phương so với người đang xem. */
public record FriendRequestResponse(
        String requestId,
        String userId,          // userId của người kia
        String username,
        String displayName,
        String direction,       // "incoming" | "outgoing"
        Instant createdAt
) {}
