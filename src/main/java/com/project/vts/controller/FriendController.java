// src/main/java/com/project/vts/controller/FriendController.java
package com.project.vts.controller;

import com.project.vts.dto.request.SendFriendRequest;
import com.project.vts.dto.response.FriendRequestResponse;
import com.project.vts.dto.response.FriendResponse;
import com.project.vts.dto.response.SendFriendResult;
import com.project.vts.exception.BadRequestException;
import com.project.vts.model.User;
import com.project.vts.repository.UserRepository;
import com.project.vts.service.FriendshipService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/friends")
public class FriendController {

    private final FriendshipService friendshipService;
    private final UserRepository userRepository;

    public FriendController(FriendshipService friendshipService, UserRepository userRepository) {
        this.friendshipService = friendshipService;
        this.userRepository = userRepository;
    }

    /** Danh sách bạn bè ACCEPTED của mình. */
    @GetMapping
    public List<FriendResponse> friends(Authentication auth) {
        return friendshipService.listFriends(currentUserId(auth));
    }

    /** Lời mời đang chờ mình xử lý. */
    @GetMapping("/requests/incoming")
    public List<FriendRequestResponse> incoming(Authentication auth) {
        return friendshipService.listIncoming(currentUserId(auth));
    }

    /** Lời mời mình đã gửi, đang chờ. */
    @GetMapping("/requests/outgoing")
    public List<FriendRequestResponse> outgoing(Authentication auth) {
        return friendshipService.listOutgoing(currentUserId(auth));
    }

    /** Gửi lời mời theo username. */
    @PostMapping("/requests")
    public SendFriendResult send(@Valid @RequestBody SendFriendRequest req, Authentication auth) {
        return friendshipService.sendRequest(currentUserId(auth), req.username());
    }

    /** Chấp nhận — chỉ người nhận (addressee). */
    @PostMapping("/requests/{id}/accept")
    public void accept(@PathVariable String id, Authentication auth) {
        friendshipService.accept(id, currentUserId(auth));
    }

    /** Từ chối — chỉ người nhận (addressee). */
    @PostMapping("/requests/{id}/decline")
    public void decline(@PathVariable String id, Authentication auth) {
        friendshipService.decline(id, currentUserId(auth));
    }

    /** Huỷ lời mời đã gửi — chỉ người gửi (requester). */
    @DeleteMapping("/requests/{id}")
    public void cancel(@PathVariable String id, Authentication auth) {
        friendshipService.cancel(id, currentUserId(auth));
    }

    /** Huỷ kết bạn — một trong hai bên. */
    @DeleteMapping("/{userId}")
    public void unfriend(@PathVariable("userId") String otherUserId, Authentication auth) {
        friendshipService.unfriend(currentUserId(auth), otherUserId);
    }

    /** Principal mang username (JWT subject) → đổi sang userId mà service dùng. */
    private String currentUserId(Authentication auth) {
        return userRepository.findByUsername(auth.getName())
                .map(User::getId)
                .orElseThrow(() -> new BadRequestException("Phiên đăng nhập không hợp lệ"));
    }
}
