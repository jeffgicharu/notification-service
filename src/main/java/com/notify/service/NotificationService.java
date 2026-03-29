package com.notify.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.notify.entity.DeliveryLog;
import com.notify.dto.request.BulkNotificationRequest;
import com.notify.repository.DeliveryLogRepository;
import com.notify.dto.request.SendNotificationRequest;
import com.notify.dto.response.NotificationResponse;
import com.notify.dto.response.StatsResponse;
import com.notify.entity.Notification;
import com.notify.enums.NotificationStatus;
import com.notify.enums.Priority;
import com.notify.queue.NotificationQueue;
import com.notify.repository.NotificationRepository;
import com.notify.template.TemplateEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final DeliveryLogRepository deliveryLogRepository;
    private final NotificationQueue queue;
    private final TemplateEngine templateEngine;
    private final ObjectMapper objectMapper;

    @Value("${notification.retry.max-attempts:3}")
    private int maxAttempts;

    @Transactional
    public NotificationResponse send(SendNotificationRequest request) {
        // Recipient rate limit check
        if (isRecipientRateLimited(request.getRecipient())) {
            throw new IllegalStateException("Recipient " + request.getRecipient()
                    + " has exceeded the limit of " + MAX_PER_RECIPIENT_PER_HOUR + " notifications per hour");
        }

        // Idempotency check
        Optional<Notification> existing = notificationRepository
                .findByIdempotencyKey(request.getIdempotencyKey());
        if (existing.isPresent()) {
            log.info("Duplicate notification detected: {}", request.getIdempotencyKey());
            return toResponse(existing.get());
        }

        // Resolve template if specified
        String subject = request.getSubject();
        String body = request.getBody();

        if (request.getTemplateId() != null) {
            if (!templateEngine.exists(request.getTemplateId())) {
                throw new IllegalArgumentException("Unknown template: " + request.getTemplateId());
            }
            var resolved = templateEngine.resolve(request.getTemplateId(), request.getTemplateParams());
            subject = resolved.subject();
            body = resolved.body();
        }

        if (body == null || body.isBlank()) {
            throw new IllegalArgumentException("Message body is required (provide body or valid templateId)");
        }

        String templateParamsJson = null;
        if (request.getTemplateParams() != null) {
            try {
                templateParamsJson = objectMapper.writeValueAsString(request.getTemplateParams());
            } catch (JsonProcessingException e) {
                templateParamsJson = request.getTemplateParams().toString();
            }
        }

        Notification notification = Notification.builder()
                .idempotencyKey(request.getIdempotencyKey())
                .channel(request.getChannel())
                .recipient(request.getRecipient())
                .subject(subject)
                .body(body)
                .status(NotificationStatus.QUEUED)
                .priority(request.getPriority() != null ? request.getPriority() : Priority.NORMAL)
                .templateId(request.getTemplateId())
                .templateParams(templateParamsJson)
                .callbackUrl(request.getCallbackUrl())
                .attemptCount(0)
                .maxAttempts(maxAttempts)
                .scheduledAt(request.getScheduledAt())
                .build();

        notificationRepository.save(notification);
        queue.enqueue(notification);

        log.info("Notification queued: {} → {} via {} (priority: {})",
                notification.getId(), notification.getRecipient(),
                notification.getChannel(), notification.getPriority());

        return toResponse(notification);
    }

    @Transactional
    public List<NotificationResponse> sendBulk(BulkNotificationRequest request) {
        List<NotificationResponse> responses = new ArrayList<>();

        for (int i = 0; i < request.getRecipients().size(); i++) {
            String recipient = request.getRecipients().get(i);

            SendNotificationRequest single = new SendNotificationRequest();
            single.setChannel(request.getChannel());
            single.setRecipient(recipient);
            single.setSubject(request.getSubject());
            single.setBody(request.getBody());
            single.setTemplateId(request.getTemplateId());
            single.setTemplateParams(request.getTemplateParams());
            single.setPriority(request.getPriority());
            single.setIdempotencyKey(request.getBatchId() + "-" + i);

            responses.add(send(single));
        }

        log.info("Bulk notification: {} messages queued (batch: {})",
                responses.size(), request.getBatchId());
        return responses;
    }

    public NotificationResponse getById(Long id) {
        Notification notification = notificationRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found: " + id));
        return toResponse(notification);
    }

    public NotificationResponse getByKey(String idempotencyKey) {
        Notification notification = notificationRepository.findByIdempotencyKey(idempotencyKey)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found: " + idempotencyKey));
        return toResponse(notification);
    }

    public Page<NotificationResponse> getByRecipient(String recipient, Pageable pageable) {
        return notificationRepository.findByRecipientOrderByCreatedAtDesc(recipient, pageable)
                .map(this::toResponse);
    }

    public Page<NotificationResponse> getByStatus(NotificationStatus status, Pageable pageable) {
        return notificationRepository.findByStatusOrderByCreatedAtDesc(status, pageable)
                .map(this::toResponse);
    }

    public StatsResponse getStats() {
        long total = notificationRepository.count();
        LocalDateTime since24h = LocalDateTime.now().minusHours(24);
        long last24h = notificationRepository.countSince(since24h);
        long delivered24h = notificationRepository.countDeliveredSince(since24h);

        Map<String, Long> byStatus = notificationRepository.countByStatus().stream()
                .collect(Collectors.toMap(
                        row -> row[0].toString(),
                        row -> (Long) row[1],
                        (a, b) -> a,
                        LinkedHashMap::new));

        Map<String, Long> byChannel = notificationRepository.countByChannel().stream()
                .collect(Collectors.toMap(
                        row -> row[0].toString(),
                        row -> (Long) row[1],
                        (a, b) -> a,
                        LinkedHashMap::new));

        double rate = last24h > 0 ? (double) delivered24h / last24h * 100 : 0;

        boolean healthy = rate >= 90.0 || last24h == 0;
        String healthStatus = rate >= 95 ? "EXCELLENT" : rate >= 90 ? "GOOD" : rate >= 75 ? "DEGRADED" : "CRITICAL";

        return StatsResponse.builder()
                .totalNotifications(total)
                .last24Hours(last24h)
                .deliveredLast24Hours(delivered24h)
                .deliveryRateLast24Hours(Math.round(rate * 100.0) / 100.0)
                .byStatus(byStatus)
                .byChannel(byChannel)
                .queueSize(queue.size())
                .deliveryRateHealthy(healthy)
                .healthStatus(healthStatus)
                .build();
    }

    // ─── DELIVERY LOGS ───────────────────────────────────────────────

    public List<DeliveryLog> getDeliveryLogs(Long notificationId) {
        return deliveryLogRepository.findByNotificationIdOrderByCreatedAtDesc(notificationId);
    }

    // ─── CANCEL AND RESEND ──────────────────────────────────────────

    @Transactional
    public NotificationResponse cancel(Long notificationId) {
        Notification n = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found: " + notificationId));
        if (n.getStatus() != NotificationStatus.QUEUED && n.getStatus() != NotificationStatus.RETRYING) {
            throw new IllegalStateException("Only QUEUED or RETRYING notifications can be cancelled. Current: " + n.getStatus());
        }
        n.setStatus(NotificationStatus.FAILED);
        n.setLastError("Cancelled by user");
        notificationRepository.save(n);
        log.info("Notification {} cancelled", notificationId);
        return toResponse(n);
    }

    @Transactional
    public NotificationResponse resend(Long notificationId) {
        Notification n = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException("Notification not found: " + notificationId));
        if (n.getStatus() != NotificationStatus.FAILED) {
            throw new IllegalStateException("Only FAILED notifications can be resent. Current: " + n.getStatus());
        }
        n.setStatus(NotificationStatus.QUEUED);
        n.setAttemptCount(0);
        n.setLastError(null);
        notificationRepository.save(n);
        queue.enqueue(n);
        log.info("Notification {} re-queued for delivery", notificationId);
        return toResponse(n);
    }

    // ─── RATE LIMITING ──────────────────────────────────────────────

    private static final int MAX_PER_RECIPIENT_PER_HOUR = 10;

    public boolean isRecipientRateLimited(String recipient) {
        long count = notificationRepository.countByRecipientSince(
                recipient, LocalDateTime.now().minusHours(1));
        return count >= MAX_PER_RECIPIENT_PER_HOUR;
    }

    // ─── CHANNEL HEALTH ─────────────────────────────────────────────

    public Map<String, Object> getChannelHealth() {
        Map<String, Object> health = new LinkedHashMap<>();
        for (var channel : com.notify.enums.Channel.values()) {
            Page<Notification> recent = notificationRepository.findByChannelOrderByCreatedAtDesc(
                    channel, org.springframework.data.domain.PageRequest.of(0, 100));
            long total = recent.getTotalElements();
            long delivered = recent.getContent().stream()
                    .filter(n -> n.getStatus() == NotificationStatus.DELIVERED).count();
            long failed = recent.getContent().stream()
                    .filter(n -> n.getStatus() == NotificationStatus.FAILED).count();
            double rate = total > 0 ? (double) delivered / total * 100 : 0;
            String status = rate >= 95 ? "HEALTHY" : rate >= 80 ? "DEGRADED" : total == 0 ? "NO_DATA" : "CRITICAL";
            health.put(channel.name(), Map.of(
                    "total", total, "delivered", delivered, "failed", failed,
                    "deliveryRate", Math.round(rate * 100.0) / 100.0, "status", status));
        }
        return health;
    }

    // ─── SCHEDULED ──────────────────────────────────────────────────

    public Page<NotificationResponse> getScheduled(Pageable pageable) {
        return notificationRepository.findByStatusAndScheduledAtIsNotNullOrderByScheduledAtAsc(
                NotificationStatus.QUEUED, pageable).map(this::toResponse);
    }

    private NotificationResponse toResponse(Notification n) {
        return NotificationResponse.builder()
                .id(n.getId())
                .idempotencyKey(n.getIdempotencyKey())
                .channel(n.getChannel())
                .recipient(n.getRecipient())
                .subject(n.getSubject())
                .status(n.getStatus())
                .priority(n.getPriority())
                .attemptCount(n.getAttemptCount())
                .maxAttempts(n.getMaxAttempts())
                .lastError(n.getLastError())
                .scheduledAt(n.getScheduledAt())
                .deliveredAt(n.getDeliveredAt())
                .createdAt(n.getCreatedAt())
                .build();
    }
}
