// src/main/java/com/project/vts/dto/request/RegisterRequest.java
package com.project.vts.dto.request;

import jakarta.validation.constraints.*;

public record RegisterRequest(
        @NotBlank @Size(min = 3, max = 30) String username,
        @Email String email,
        @NotBlank @Size(min = 6, max = 100) String password,
        @NotBlank String displayName
) {}