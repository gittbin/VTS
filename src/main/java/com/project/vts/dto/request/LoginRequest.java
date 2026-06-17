// src/main/java/com/project/vts/dto/request/LoginRequest.java
package com.project.vts.dto.request;

import jakarta.validation.constraints.NotBlank;

public record LoginRequest(
        @NotBlank String username,
        @NotBlank String password
) {}