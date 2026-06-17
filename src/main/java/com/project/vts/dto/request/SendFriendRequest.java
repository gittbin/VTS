// src/main/java/com/project/vts/dto/request/SendFriendRequest.java
package com.project.vts.dto.request;

import jakarta.validation.constraints.NotBlank;

/** Body cho POST /api/friends/requests — gửi lời mời theo username. */
public record SendFriendRequest(
        @NotBlank String username
) {}
