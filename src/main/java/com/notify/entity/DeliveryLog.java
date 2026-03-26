package com.notify.entity;

import jakarta.persistence.*;
import lombok.*;

import java.time.LocalDateTime;

@Entity
@Table(name = "delivery_logs", indexes = {
    @Index(name = "idx_dlog_notif", columnList = "notification_id")
})
@Getter @Setter
@NoArgsConstructor @AllArgsConstructor
@Builder
public class DeliveryLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "notification_id", nullable = false)
    private Notification notification;

    private int attemptNumber;

    @Column(nullable = false, length = 15)
    private String status;

    @Column(columnDefinition = "TEXT")
    private String detail;

    private long durationMs;

    @Column(nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @PrePersist
    protected void onCreate() {
        createdAt = LocalDateTime.now();
    }
}
