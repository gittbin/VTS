// src/main/java/com/project/vts/model/CallHistory.java
package com.project.vts.model;

import lombok.*;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

@Document(collection = "call_history")
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class CallHistory {

    @Id
    private String id;              // = callId do client sinh (UUID)

    @Indexed private String callerId;
    @Indexed private String calleeId;

    private String callerUsername;  // denormalize để hiển thị không cần join
    private String calleeUsername;
    private String callerName;
    private String calleeName;

    private String type;            // VIDEO | AUDIO
    private String status;          // RINGING | ONGOING | COMPLETED | REJECTED | MISSED

    private Instant createdAt;      // lúc bắt đầu đổ chuông
    private Instant startedAt;      // lúc nghe máy
    private Instant endedAt;
    private long durationSeconds;
}