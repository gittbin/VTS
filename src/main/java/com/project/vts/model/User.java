// src/main/java/com/project/vts/model/User.java
package com.project.vts.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;
import java.util.List;

@Document(collection = "users")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    private String id;

    @Indexed(unique = true)
    private String username;

    @Indexed(unique = true, sparse = true)
    private String email;

    private String password;        // luôn lưu dạng BCrypt hash, KHÔNG bao giờ plaintext
    private String displayName;
    private String avatarUrl;

    private boolean online;         // "trạng thái biết lần cuối" — nguồn chân lý nằm ở tầng signaling
    private Instant lastSeen;

    private List<String> roles;

    private Instant createdAt;
    private Instant updatedAt;
}