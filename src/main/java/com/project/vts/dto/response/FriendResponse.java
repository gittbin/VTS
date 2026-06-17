// src/main/java/com/project/vts/dto/response/FriendResponse.java
package com.project.vts.dto.response;

/** Bạn bè trong danh sách. KHÔNG lộ email/roles/lastSeen (least disclosure). */
public record FriendResponse(
        String id,
        String username,
        String displayName,
        boolean online
) {}
