// src/main/java/com/project/vts/dto/response/UserSearchResponse.java
package com.project.vts.dto.response;

/**
 * Kết quả tìm người để kết bạn (theo username chính xác).
 * {@code relationship}: SELF | NONE | PENDING_OUT | PENDING_IN | FRIEND — để UI hiển thị đúng nút.
 */
public record UserSearchResponse(
        String userId,
        String username,
        String displayName,
        String relationship
) {}
