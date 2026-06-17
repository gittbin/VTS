// src/main/java/com/project/vts/dto/response/SendFriendResult.java
package com.project.vts.dto.response;

/**
 * Kết quả gửi lời mời: status = "PENDING" (đã gửi, chờ phản hồi)
 * hoặc "ACCEPTED" (đối phương đã gửi mình trước → tự động thành bạn bè).
 */
public record SendFriendResult(
        String status
) {}
