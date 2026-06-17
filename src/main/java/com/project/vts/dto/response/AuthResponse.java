// src/main/java/com/project/vts/dto/response/AuthResponse.java
package com.project.vts.dto.response;

public record AuthResponse(
        String token,
        String tokenType,
        UserResponse user
) {}