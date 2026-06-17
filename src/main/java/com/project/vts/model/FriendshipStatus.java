// src/main/java/com/project/vts/model/FriendshipStatus.java
package com.project.vts.model;

/** Trạng thái quan hệ bạn bè. Lưu trong Mongo dưới dạng tên enum (PENDING/ACCEPTED). */
public enum FriendshipStatus {
    PENDING,    // đã gửi lời mời, chờ phản hồi
    ACCEPTED    // đã là bạn bè
}
