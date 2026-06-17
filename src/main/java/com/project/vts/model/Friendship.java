// src/main/java/com/project/vts/model/Friendship.java
package com.project.vts.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Một bản ghi cho MỘT cặp người dùng (bất kể ai gửi trước), nhờ {@code pairKey} unique.
 * requesterId/addresseeId vẫn cần để biết AI gửi cho AI → áp quyền accept/cancel.
 */
@Document(collection = "friendships")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class Friendship {

    @Id
    private String id;

    @Indexed private String requesterId;   // userId người GỬI lời mời
    @Indexed private String addresseeId;   // userId người NHẬN lời mời

    private FriendshipStatus status;        // PENDING | ACCEPTED

    @Indexed(unique = true)
    private String pairKey;                 // hai userId sắp xếp tăng dần, nối '_' → chống trùng cặp ở mọi hướng

    private Instant createdAt;
    private Instant updatedAt;
    private Instant acceptedAt;             // null nếu chưa chấp nhận
}
