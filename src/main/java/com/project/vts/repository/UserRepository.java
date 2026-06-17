// src/main/java/com/project/vts/repository/UserRepository.java
package com.project.vts.repository;

import com.project.vts.model.User;
import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {
    Optional<User> findByUsername(String username);
    boolean existsByUsername(String username);
    boolean existsByEmail(String email);

    // Tìm để kết bạn: khớp MỘT PHẦN theo username HOẶC tên hiển thị, không phân biệt hoa/thường, tối đa 10.
    // (Containing của Spring Data Mongo tự escape ký tự regex → an toàn trước injection.)
    List<User> findTop10ByUsernameContainingIgnoreCaseOrDisplayNameContainingIgnoreCase(String username, String displayName);
}