// src/test/java/com/project/vts/service/FriendshipServiceTest.java
package com.project.vts.service;

import com.project.vts.dto.response.SendFriendResult;
import com.project.vts.exception.BadRequestException;
import com.project.vts.exception.ConflictException;
import com.project.vts.exception.ForbiddenException;
import com.project.vts.exception.NotFoundException;
import com.project.vts.model.Friendship;
import com.project.vts.model.FriendshipStatus;
import com.project.vts.model.User;
import com.project.vts.repository.FriendshipRepository;
import com.project.vts.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/** Kiểm thử máy trạng thái + phân quyền của FriendshipService (THIET-KE-FRIEND-ONLY §12.1). */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class FriendshipServiceTest {

    @Mock FriendshipRepository friendshipRepository;
    @Mock UserRepository userRepository;
    @InjectMocks FriendshipService service;

    private User user(String id, String username) {
        return User.builder().id(id).username(username).displayName(username).build();
    }

    private Friendship pending(String id, String requesterId, String addresseeId) {
        return Friendship.builder()
                .id(id).requesterId(requesterId).addresseeId(addresseeId)
                .status(FriendshipStatus.PENDING)
                .pairKey(FriendshipService.pairKey(requesterId, addresseeId))
                .createdAt(Instant.now()).updatedAt(Instant.now())
                .build();
    }

    // ----- pairKey -----
    @Test
    void pairKey_doiXung_batKeThuTu() {
        assertThat(FriendshipService.pairKey("a", "b")).isEqualTo(FriendshipService.pairKey("b", "a"));
    }

    // ----- sendRequest -----
    @Test
    void guiLoiMoiHopLe_taoPENDING() {
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user("B", "bob")));
        when(friendshipRepository.findByPairKey(FriendshipService.pairKey("A", "B"))).thenReturn(Optional.empty());

        SendFriendResult result = service.sendRequest("A", "bob");

        assertThat(result.status()).isEqualTo("PENDING");
        ArgumentCaptor<Friendship> cap = ArgumentCaptor.forClass(Friendship.class);
        verify(friendshipRepository).save(cap.capture());
        assertThat(cap.getValue().getRequesterId()).isEqualTo("A");
        assertThat(cap.getValue().getAddresseeId()).isEqualTo("B");
        assertThat(cap.getValue().getStatus()).isEqualTo(FriendshipStatus.PENDING);
    }

    @Test
    void guiLoiMoiChoChinhMinh_loi() {
        when(userRepository.findByUsername("alice")).thenReturn(Optional.of(user("A", "alice")));
        assertThatThrownBy(() -> service.sendRequest("A", "alice"))
                .isInstanceOf(BadRequestException.class);
        verify(friendshipRepository, never()).save(any());
    }

    @Test
    void guiLoiMoiKhiDaCoPENDINGCungChieu_conflict() {
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user("B", "bob")));
        when(friendshipRepository.findByPairKey(FriendshipService.pairKey("A", "B")))
                .thenReturn(Optional.of(pending("f1", "A", "B")));   // A đã gửi B từ trước

        assertThatThrownBy(() -> service.sendRequest("A", "bob"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void guiLoiMoiKhiDaLaBan_conflict() {
        Friendship accepted = pending("f1", "A", "B");
        accepted.setStatus(FriendshipStatus.ACCEPTED);
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user("B", "bob")));
        when(friendshipRepository.findByPairKey(FriendshipService.pairKey("A", "B"))).thenReturn(Optional.of(accepted));

        assertThatThrownBy(() -> service.sendRequest("A", "bob"))
                .isInstanceOf(ConflictException.class);
    }

    @Test
    void aGuiB_roiBGuiA_thiAutoAccept() {
        // B đã gửi A trước (requester=B, addressee=A). Giờ A gửi B → tự động thành bạn.
        when(userRepository.findByUsername("bob")).thenReturn(Optional.of(user("B", "bob")));
        when(friendshipRepository.findByPairKey(FriendshipService.pairKey("A", "B")))
                .thenReturn(Optional.of(pending("f1", "B", "A")));

        SendFriendResult result = service.sendRequest("A", "bob");

        assertThat(result.status()).isEqualTo("ACCEPTED");
        ArgumentCaptor<Friendship> cap = ArgumentCaptor.forClass(Friendship.class);
        verify(friendshipRepository).save(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo(FriendshipStatus.ACCEPTED);
        assertThat(cap.getValue().getAcceptedAt()).isNotNull();
    }

    // ----- accept / decline / cancel authz -----
    @Test
    void acceptBoiNguoiKhongPhaiAddressee_403() {
        when(friendshipRepository.findById("f1")).thenReturn(Optional.of(pending("f1", "B", "A")));
        assertThatThrownBy(() -> service.accept("f1", "C"))   // C không phải addressee (A)
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void acceptHopLe_chuyenACCEPTED() {
        when(friendshipRepository.findById("f1")).thenReturn(Optional.of(pending("f1", "B", "A")));
        service.accept("f1", "A");
        ArgumentCaptor<Friendship> cap = ArgumentCaptor.forClass(Friendship.class);
        verify(friendshipRepository).save(cap.capture());
        assertThat(cap.getValue().getStatus()).isEqualTo(FriendshipStatus.ACCEPTED);
    }

    @Test
    void cancelBoiNguoiKhongPhaiRequester_403() {
        when(friendshipRepository.findById("f1")).thenReturn(Optional.of(pending("f1", "A", "B")));
        assertThatThrownBy(() -> service.cancel("f1", "B"))   // B là addressee, không được cancel
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void declineBoiNguoiKhongPhaiAddressee_403() {
        when(friendshipRepository.findById("f1")).thenReturn(Optional.of(pending("f1", "A", "B")));
        assertThatThrownBy(() -> service.decline("f1", "A"))   // A là requester, không được decline
                .isInstanceOf(ForbiddenException.class);
    }

    @Test
    void acceptLoiMoiKhongTonTai_404() {
        when(friendshipRepository.findById("zzz")).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.accept("zzz", "A"))
                .isInstanceOf(NotFoundException.class);
    }

    // ----- unfriend -----
    @Test
    void unfriendKhiChuaLaBan_404() {
        when(friendshipRepository.findByPairKey(FriendshipService.pairKey("A", "B"))).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.unfriend("A", "B"))
                .isInstanceOf(NotFoundException.class);
    }

    // ----- areFriends -----
    @Test
    void areFriends_trueKhiACCEPTED_falseKhiPENDING() {
        Friendship accepted = pending("f1", "A", "B");
        accepted.setStatus(FriendshipStatus.ACCEPTED);
        when(friendshipRepository.findByPairKey(FriendshipService.pairKey("A", "B"))).thenReturn(Optional.of(accepted));
        assertThat(service.areFriends("A", "B")).isTrue();

        when(friendshipRepository.findByPairKey(FriendshipService.pairKey("A", "C")))
                .thenReturn(Optional.of(pending("f2", "A", "C")));
        assertThat(service.areFriends("A", "C")).isFalse();

        assertThat(service.areFriends("A", "A")).isFalse();   // chính mình → không phải bạn
    }
}
