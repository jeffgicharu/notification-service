package com.notify.entity;

import com.notify.enums.Channel;
import com.notify.enums.NotificationStatus;
import com.notify.enums.Priority;
import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "notifications", indexes = {
    @Index(name = "idx_notif_status", columnList = "status"),
    @Index(name = "idx_notif_recipient", columnList = "recipient"),
    @Index(name = "idx_notif_created", columnList = "createdAt"),
    @Index(name = "idx_notif_idempotency", columnList = "idempotencyKey", unique = true)
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class Notification {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String idempotencyKey;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Channel channel;

    @Column(nullable = false)
    private String recipient;

    private String subject;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 15)
    private NotificationStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 10)
    private Priority priority;

    private String templateId;

    @Column(columnDefinition = "TEXT")
    private String templateParams;

    private String callbackUrl;

    private int attemptCount;

    private int maxAttempts;

    private String lastError;

    private LocalDateTime scheduledAt;

    private LocalDateTime processedAt;

    private LocalDateTime deliveredAt;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
        updatedAt = LocalDateTime.now();
        if (status == null) status = NotificationStatus.QUEUED;
        if (priority == null) priority = Priority.NORMAL;
    }

    @PreUpdate
    protected void onUpdate() {
        updatedAt = LocalDateTime.now();
    }

    public void incrementAttempts() {
        this.attemptCount++;
    }

    public boolean canRetry() {
        return attemptCount < maxAttempts;
    }
}
