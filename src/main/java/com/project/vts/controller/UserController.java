// src/main/java/com/project/vts/controller/UserController.java
package com.project.vts.controller;

import com.project.vts.dto.response.UserResponse;
import com.project.vts.service.UserService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/users")
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    /** Danh sách mọi người (trừ chính mình). */
    @GetMapping
    public List<UserResponse> listUsers(Authentication authentication) {
        // principal là username lấy từ JWT → authentication.getName()
        return userService.listOthers(authentication.getName());
    }

    /** Thông tin của chính mình. */
    @GetMapping("/me")
    public UserResponse me(Authentication authentication) {
        return userService.getByUsername(authentication.getName());
    }
}