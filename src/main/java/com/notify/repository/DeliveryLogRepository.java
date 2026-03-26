package com.notify.repository;

import com.notify.entity.DeliveryLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface DeliveryLogRepository extends JpaRepository<DeliveryLog, Long> {

    List<DeliveryLog> findByNotificationIdOrderByCreatedAtDesc(Long notificationId);
}
