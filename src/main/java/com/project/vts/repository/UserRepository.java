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

    // Tất cả user trừ chính mình, sắp xếp theo tên hiển thị cho danh sách "Mọi người"
    List<User> findByUsernameNotOrderByDisplayNameAsc(String username);
}