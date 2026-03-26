package com.notify.repository;

import com.notify.entity.Notification;
import com.notify.enums.Channel;
import com.notify.enums.NotificationStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface NotificationRepository extends JpaRepository<Notification, Long> {

    Optional<Notification> findByIdempotencyKey(String idempotencyKey);

    boolean existsByIdempotencyKey(String idempotencyKey);

    Page<Notification> findByRecipientOrderByCreatedAtDesc(String recipient, Pageable pageable);

    Page<Notification> findByStatusOrderByCreatedAtDesc(NotificationStatus status, Pageable pageable);

    Page<Notification> findByChannelOrderByCreatedAtDesc(Channel channel, Pageable pageable);

    @Query("SELECT n FROM Notification n WHERE n.status = :status AND " +
           "(n.scheduledAt IS NULL OR n.scheduledAt <= :now) " +
           "ORDER BY n.priority DESC, n.createdAt ASC")
    List<Notification> findPendingNotifications(
            @Param("status") NotificationStatus status,
            @Param("now") LocalDateTime now,
            Pageable pageable);

    @Query("SELECT n.status, COUNT(n) FROM Notification n GROUP BY n.status")
    List<Object[]> countByStatus();

    @Query("SELECT n.channel, COUNT(n) FROM Notification n GROUP BY n.channel")
    List<Object[]> countByChannel();

    @Query("SELECT COUNT(n) FROM Notification n WHERE n.createdAt >= :since")
    long countSince(@Param("since") LocalDateTime since);

    @Query("SELECT COUNT(n) FROM Notification n WHERE n.status = 'DELIVERED' AND n.createdAt >= :since")
    long countDeliveredSince(@Param("since") LocalDateTime since);
}
