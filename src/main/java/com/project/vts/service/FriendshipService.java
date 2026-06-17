// src/main/java/com/project/vts/service/FriendshipService.java
package com.project.vts.service;

import com.project.vts.dto.response.FriendRequestResponse;
import com.project.vts.dto.response.FriendResponse;
import com.project.vts.dto.response.SendFriendResult;
import com.project.vts.dto.response.UserSearchResponse;
import com.project.vts.exception.BadRequestException;
import com.project.vts.exception.ConflictException;
import com.project.vts.exception.ForbiddenException;
import com.project.vts.exception.NotFoundException;
import com.project.vts.model.Friendship;
import com.project.vts.model.FriendshipStatus;
import com.project.vts.model.User;
import com.project.vts.repository.FriendshipRepository;
import com.project.vts.repository.UserRepository;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.StreamSupport;

/**
 * Quản lý quan hệ bạn bè + máy trạng thái (xem THIET-KE-FRIEND-ONLY §4).
 * Đây là nơi enforce toàn bộ validation & phân quyền — controller chỉ mỏng.
 */
@Service
public class FriendshipService {

    private final FriendshipRepository friendshipRepository;
    private final UserRepository userRepository;

    public FriendshipService(FriendshipRepository friendshipRepository, UserRepository userRepository) {
        this.friendshipRepository = friendshipRepository;
        this.userRepository = userRepository;
    }

    /** Khoá cặp chuẩn hoá: hai userId sắp xếp tăng dần, nối '_'. Chống trùng cặp ở mọi hướng. */
    public static String pairKey(String a, String b) {
        return (a.compareTo(b) <= 0) ? a + "_" + b : b + "_" + a;
    }

    // ---------- Lệnh (thay đổi trạng thái) ----------

    /** Gửi lời mời theo username. Nếu đối phương đã gửi mình trước → tự động thành bạn (auto-accept). */
    public SendFriendResult sendRequest(String requesterId, String targetUsername) {
        User target = userRepository.findByUsername(targetUsername)
                .orElseThrow(() -> new BadRequestException("Không tìm thấy người dùng @" + targetUsername));
        String addresseeId = target.getId();
        if (requesterId.equals(addresseeId))
            throw new BadRequestException("Không thể tự kết bạn với chính mình");

        String pk = pairKey(requesterId, addresseeId);
        Optional<Friendship> existing = friendshipRepository.findByPairKey(pk);
        if (existing.isPresent()) return resolveExisting(existing.get(), requesterId);

        Instant now = Instant.now();
        try {
            friendshipRepository.save(Friendship.builder()
                    .requesterId(requesterId).addresseeId(addresseeId)
                    .status(FriendshipStatus.PENDING).pairKey(pk)
                    .createdAt(now).updatedAt(now)
                    .build());
            return new SendFriendResult("PENDING");
        } catch (DuplicateKeyException race) {
            // Hai người gửi cho nhau gần như cùng lúc → bản ghi chiều ngược vừa được tạo: xử như đã có sẵn.
            Friendship other = friendshipRepository.findByPairKey(pk)
                    .orElseThrow(() -> new ConflictException("Không thể gửi lời mời lúc này, thử lại."));
            return resolveExisting(other, requesterId);
        }
    }

    private SendFriendResult resolveExisting(Friendship f, String requesterId) {
        if (f.getStatus() == FriendshipStatus.ACCEPTED)
            throw new ConflictException("Hai bạn đã là bạn bè");
        // PENDING:
        if (f.getRequesterId().equals(requesterId))
            throw new ConflictException("Đã gửi lời mời, đang chờ phản hồi");
        markAccepted(f);                       // người kia đã mời mình trước → auto-accept
        return new SendFriendResult("ACCEPTED");
    }

    public void accept(String requestId, String currentUserId) {
        Friendship f = getOrThrow(requestId);
        if (!currentUserId.equals(f.getAddresseeId()))
            throw new ForbiddenException("Bạn không phải người nhận lời mời này");
        if (f.getStatus() != FriendshipStatus.PENDING)
            throw new ConflictException("Lời mời không còn ở trạng thái chờ");
        markAccepted(f);
    }

    public void decline(String requestId, String currentUserId) {
        Friendship f = getOrThrow(requestId);
        if (!currentUserId.equals(f.getAddresseeId()))
            throw new ForbiddenException("Bạn không phải người nhận lời mời này");
        if (f.getStatus() != FriendshipStatus.PENDING)
            throw new ConflictException("Lời mời không còn ở trạng thái chờ");
        friendshipRepository.delete(f);
    }

    public void cancel(String requestId, String currentUserId) {
        Friendship f = getOrThrow(requestId);
        if (!currentUserId.equals(f.getRequesterId()))
            throw new ForbiddenException("Bạn không phải người gửi lời mời này");
        if (f.getStatus() != FriendshipStatus.PENDING)
            throw new ConflictException("Lời mời không còn ở trạng thái chờ");
        friendshipRepository.delete(f);
    }

    public void unfriend(String currentUserId, String otherUserId) {
        if (currentUserId.equals(otherUserId)) throw new BadRequestException("Tham số không hợp lệ");
        Friendship f = friendshipRepository.findByPairKey(pairKey(currentUserId, otherUserId))
                .orElseThrow(() -> new NotFoundException("Hai bạn chưa phải bạn bè"));
        // pairKey đảm bảo currentUserId là một trong hai đương sự → được phép xoá.
        friendshipRepository.delete(f);
    }

    private void markAccepted(Friendship f) {
        Instant now = Instant.now();
        f.setStatus(FriendshipStatus.ACCEPTED);
        f.setAcceptedAt(now);
        f.setUpdatedAt(now);
        friendshipRepository.save(f);
    }

    private Friendship getOrThrow(String requestId) {
        return friendshipRepository.findById(requestId)
                .orElseThrow(() -> new NotFoundException("Lời mời không tồn tại hoặc đã bị huỷ"));
    }

    // ---------- Truy vấn ----------

    public List<FriendResponse> listFriends(String userId) {
        List<String> ids = friendshipRepository.findAcceptedForUser(userId).stream()
                .map(f -> otherId(f, userId)).distinct().toList();
        return StreamSupport.stream(userRepository.findAllById(ids).spliterator(), false)
                .sorted(Comparator.comparing(u -> sortKey(u)))
                .map(u -> new FriendResponse(u.getId(), u.getUsername(), u.getDisplayName(), u.isOnline()))
                .toList();
    }

    public List<FriendRequestResponse> listIncoming(String userId) {
        return friendshipRepository.findByAddresseeIdAndStatus(userId, FriendshipStatus.PENDING).stream()
                .map(f -> toRequestResponse(f, f.getRequesterId(), "incoming"))
                .flatMap(Optional::stream)
                .toList();
    }

    public List<FriendRequestResponse> listOutgoing(String userId) {
        return friendshipRepository.findByRequesterIdAndStatus(userId, FriendshipStatus.PENDING).stream()
                .map(f -> toRequestResponse(f, f.getAddresseeId(), "outgoing"))
                .flatMap(Optional::stream)
                .toList();
    }

    /**
     * Tìm người để kết bạn theo username HOẶC tên hiển thị (khớp một phần, không phân biệt hoa/thường,
     * tối đa 10 kết quả). Loại chính mình, kèm quan hệ hiện tại để UI hiển thị đúng nút.
     */
    public List<UserSearchResponse> search(String currentUserId, String query) {
        String q = query == null ? "" : query.trim();
        if (q.isEmpty()) return List.of();
        return userRepository
                .findTop10ByUsernameContainingIgnoreCaseOrDisplayNameContainingIgnoreCase(q, q).stream()
                .filter(u -> !u.getId().equals(currentUserId))
                .map(u -> new UserSearchResponse(u.getId(), u.getUsername(), u.getDisplayName(),
                        relationshipBetween(currentUserId, u.getId())))
                .toList();
    }

    // ---------- Dùng cho tầng signaling ----------

    public boolean areFriends(String a, String b) {
        if (a == null || b == null || a.equals(b)) return false;
        return friendshipRepository.findByPairKey(pairKey(a, b))
                .map(f -> f.getStatus() == FriendshipStatus.ACCEPTED)
                .orElse(false);
    }

    public Set<String> friendIds(String userId) {
        if (userId == null) return Set.of();
        return friendshipRepository.findAcceptedForUser(userId).stream()
                .map(f -> otherId(f, userId))
                .collect(Collectors.toSet());
    }

    // ---------- Helpers ----------

    /** SELF | NONE | PENDING_OUT | PENDING_IN | FRIEND */
    public String relationshipBetween(String currentUserId, String targetId) {
        if (currentUserId.equals(targetId)) return "SELF";
        return friendshipRepository.findByPairKey(pairKey(currentUserId, targetId))
                .map(f -> f.getStatus() == FriendshipStatus.ACCEPTED ? "FRIEND"
                        : (f.getRequesterId().equals(currentUserId) ? "PENDING_OUT" : "PENDING_IN"))
                .orElse("NONE");
    }

    private Optional<FriendRequestResponse> toRequestResponse(Friendship f, String otherUserId, String direction) {
        return userRepository.findById(otherUserId).map(u ->
                new FriendRequestResponse(f.getId(), u.getId(), u.getUsername(), u.getDisplayName(),
                        direction, f.getCreatedAt()));
    }

    private static String otherId(Friendship f, String userId) {
        return f.getRequesterId().equals(userId) ? f.getAddresseeId() : f.getRequesterId();
    }

    private static String sortKey(User u) {
        String s = (u.getDisplayName() != null && !u.getDisplayName().isBlank()) ? u.getDisplayName() : u.getUsername();
        return s == null ? "" : s.toLowerCase();
    }
}
