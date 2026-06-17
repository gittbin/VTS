// src/main/java/com/project/vts/dto/response/UserResponse.java
package com.project.vts.dto.response;

public record UserResponse(
        String id,
        String username,
        String displayName,
        String email,
        boolean online
) {}