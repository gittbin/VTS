// src/main/java/com/project/vts/service/UserService.java
package com.project.vts.service;

import com.project.vts.dto.response.UserResponse;
import com.project.vts.exception.BadRequestException;
import com.project.vts.model.User;
import com.project.vts.repository.UserRepository;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class UserService {

    private final UserRepository userRepository;

    public UserService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    /** Danh sách mọi người trừ chính mình — đổ vào phần "Mọi người" ở index.html. */
    public List<UserResponse> listOthers(String currentUsername) {
        return userRepository.findByUsernameNotOrderByDisplayNameAsc(currentUsername)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    /** Thông tin user hiện tại (cho GET /api/users/me). */
    public UserResponse getByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BadRequestException("Không tìm thấy user: " + username));
        return toResponse(user);
    }

    private UserResponse toResponse(User u) {
        return new UserResponse(u.getId(), u.getUsername(), u.getDisplayName(), u.getEmail(), u.isOnline());
    }
}