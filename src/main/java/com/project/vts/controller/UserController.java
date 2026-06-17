// src/main/java/com/project/vts/controller/UserController.java
package com.project.vts.controller;

import com.project.vts.dto.response.UserResponse;
import com.project.vts.dto.response.UserSearchResponse;
import com.project.vts.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /** Thông tin của chính mình. */
    @GetMapping("/me")
    public UserResponse me(Authentication authentication) {
        return userService.getByUsername(authentication.getName());
    }

    /**
     * Tìm người để kết bạn bằng username CHÍNH XÁC.
     * Cố ý KHÔNG có endpoint liệt kê toàn bộ user (tránh enumeration — xem THIET-KE-FRIEND-ONLY §7).
     */
    @GetMapping("/search")
    public UserSearchResponse search(@RequestParam String username, Authentication authentication) {
        return userService.search(authentication.getName(), username);
    }
}
