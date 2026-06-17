// src/main/java/com/project/vts/repository/CallHistoryRepository.java
package com.project.vts.repository;

import com.project.vts.model.CallHistory;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;

public interface CallHistoryRepository extends MongoRepository<CallHistory, String> {

    List<CallHistory> findTop50ByCallerUsernameOrCalleeUsernameOrderByCreatedAtDesc(String callerUsername, String calleeUsername);

    // Cuộc gọi đang dở của 1 user (để dọn khi rớt mạng)
    @Query("{ 'status': { $in: ?1 }, $or: [ { 'callerId': ?0 }, { 'calleeId': ?0 } ] }")
    List<CallHistory> findActiveForUser(String userId, List<String> statuses);
}