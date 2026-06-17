// src/main/java/com/project/vts/service/UserService.java
package com.project.vts.service;

import com.project.vts.dto.response.UserResponse;
import com.project.vts.dto.response.UserSearchResponse;
import com.project.vts.exception.BadRequestException;
import com.project.vts.model.User;
import com.project.vts.repository.UserRepository;
import org.springframework.stereotype.Service;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final FriendshipService friendshipService;

    public UserService(UserRepository userRepository, FriendshipService friendshipService) {
        this.userRepository = userRepository;
        this.friendshipService = friendshipService;
    }

    /** Thông tin user hiện tại (cho GET /api/users/me). */
    public UserResponse getByUsername(String username) {
        User user = userRepository.findByUsername(username)
                .orElseThrow(() -> new BadRequestException("Không tìm thấy user: " + username));
        return toResponse(user);
    }

    /**
     * Tìm 1 người để kết bạn theo username chính xác (mô hình friends-only — KHÔNG liệt kê toàn bộ user).
     * Trả kèm quan hệ hiện tại để UI hiển thị đúng nút.
     */
    public UserSearchResponse search(String currentUsername, String targetUsername) {
        User me = userRepository.findByUsername(currentUsername)
                .orElseThrow(() -> new BadRequestException("Phiên đăng nhập không hợp lệ"));
        return friendshipService.search(me.getId(), targetUsername);
    }

    private UserResponse toResponse(User u) {
        return new UserResponse(u.getId(), u.getUsername(), u.getDisplayName(), u.getEmail(), u.isOnline());
    }
}
