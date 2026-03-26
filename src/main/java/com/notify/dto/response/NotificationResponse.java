package com.notify.dto.response;

import com.notify.enums.Channel;
import com.notify.enums.NotificationStatus;
import com.notify.enums.Priority;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

@Data
@Builder
@AllArgsConstructor
public class NotificationResponse {
    private Long id;
    private String idempotencyKey;
    private Channel channel;
    private String recipient;
    private String subject;
    private NotificationStatus status;
    private Priority priority;
    private int attemptCount;
    private int maxAttempts;
    private String lastError;
    private LocalDateTime scheduledAt;
    private LocalDateTime deliveredAt;
    private LocalDateTime createdAt;
}
