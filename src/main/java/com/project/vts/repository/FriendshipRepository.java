// src/main/java/com/project/vts/repository/FriendshipRepository.java
package com.project.vts.repository;

import com.project.vts.model.Friendship;
import com.project.vts.model.FriendshipStatus;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.util.List;
import java.util.Optional;

public interface FriendshipRepository extends MongoRepository<Friendship, String> {

    Optional<Friendship> findByPairKey(String pairKey);

    List<Friendship> findByRequesterIdAndStatus(String requesterId, FriendshipStatus status);
    List<Friendship> findByAddresseeIdAndStatus(String addresseeId, FriendshipStatus status);

    // Bạn bè ACCEPTED của 1 user (ở cả hai phía requester/addressee)
    @Query("{ 'status': 'ACCEPTED', $or: [ { 'requesterId': ?0 }, { 'addresseeId': ?0 } ] }")
    List<Friendship> findAcceptedForUser(String userId);
}
