package com.notify.service;

import com.notify.entity.DeliveryLog;
import com.notify.entity.Notification;
import com.notify.enums.Channel;
import com.notify.enums.NotificationStatus;
import com.notify.queue.NotificationQueue;
import com.notify.repository.DeliveryLogRepository;
import com.notify.repository.NotificationRepository;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Background worker that dequeues notifications and dispatches them
 * through the appropriate channel with exponential backoff retry.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class NotificationWorker {

    private final NotificationQueue queue;
    private final NotificationRepository notificationRepository;
    private final DeliveryLogRepository deliveryLogRepository;
    private final List<ChannelDispatcher> dispatchers;
    private final CallbackService callbackService;

    @Value("${notification.queue.worker-threads:4}")
    private int workerThreads;

    @Value("${notification.retry.initial-delay-ms:1000}")
    private long initialDelayMs;

    @Value("${notification.retry.backoff-multiplier:2.0}")
    private double backoffMultiplier;

    @Value("${notification.retry.max-delay-ms:30000}")
    private long maxDelayMs;

    private ExecutorService executor;
    private ScheduledExecutorService scheduler;
    private Map<Channel, ChannelDispatcher> dispatcherMap;
    private volatile boolean running = true;

    @PostConstruct
    void start() {
        dispatcherMap = dispatchers.stream()
                .collect(Collectors.toMap(ChannelDispatcher::getChannel, Function.identity()));

        executor = Executors.newFixedThreadPool(workerThreads, r -> {
            Thread t = new Thread(r, "notify-worker");
            t.setDaemon(true);
            return t;
        });

        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "notify-scheduler");
            t.setDaemon(true);
            return t;
        });

        // Start worker threads that poll the queue
        for (int i = 0; i < workerThreads; i++) {
            executor.submit(this::workerLoop);
        }

        // Periodically re-enqueue stuck PROCESSING notifications
        scheduler.scheduleAtFixedRate(this::recoverStuck, 60, 60, TimeUnit.SECONDS);

        log.info("Notification worker started with {} threads", workerThreads);
    }

    @PreDestroy
    void stop() {
        running = false;
        executor.shutdown();
        scheduler.shutdown();
        log.info("Notification worker stopped");
    }

    private void workerLoop() {
        while (running) {
            try {
                Long notificationId = queue.poll();
                if (notificationId == null) {
                    Thread.sleep(100); // Back off when queue is empty
                    continue;
                }
                processNotification(notificationId);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                log.error("Worker error", e);
            }
        }
    }

    private void processNotification(Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId).orElse(null);
        if (notification == null) return;

        // Skip if already delivered or not in a processable state
        if (notification.getStatus() == NotificationStatus.DELIVERED) return;

        // Check scheduled time
        if (notification.getScheduledAt() != null
                && notification.getScheduledAt().isAfter(LocalDateTime.now())) {
            // Re-enqueue for later
            queue.enqueue(notification);
            return;
        }

        notification.setStatus(NotificationStatus.PROCESSING);
        notification.setProcessedAt(LocalDateTime.now());
        notification.incrementAttempts();
        notificationRepository.save(notification);

        // Dispatch through the appropriate channel
        ChannelDispatcher dispatcher = dispatcherMap.get(notification.getChannel());
        if (dispatcher == null) {
            fail(notification, "No dispatcher for channel: " + notification.getChannel(), 0);
            return;
        }

        ChannelDispatcher.DeliveryResult result = dispatcher.deliver(notification);

        // Log the delivery attempt
        DeliveryLog deliveryLog = DeliveryLog.builder()
                .notification(notification)
                .attemptNumber(notification.getAttemptCount())
                .status(result.success() ? "DELIVERED" : "FAILED")
                .detail(result.detail())
                .durationMs(result.durationMs())
                .build();
        deliveryLogRepository.save(deliveryLog);

        if (result.success()) {
            notification.setStatus(NotificationStatus.DELIVERED);
            notification.setDeliveredAt(LocalDateTime.now());
            notificationRepository.save(notification);

            callbackService.sendCallback(notification, "DELIVERED", result.detail());

            log.info("Notification {} delivered to {} via {} (attempt {})",
                    notification.getId(), notification.getRecipient(),
                    notification.getChannel(), notification.getAttemptCount());
        } else {
            handleFailure(notification, result.detail());
        }
    }

    private void handleFailure(Notification notification, String error) {
        if (notification.canRetry()) {
            // Schedule retry with exponential backoff
            long delay = calculateBackoff(notification.getAttemptCount());
            notification.setStatus(NotificationStatus.RETRYING);
            notification.setLastError(error);
            notificationRepository.save(notification);

            scheduler.schedule(() -> {
                notification.setStatus(NotificationStatus.QUEUED);
                notificationRepository.save(notification);
                queue.enqueue(notification);
            }, delay, TimeUnit.MILLISECONDS);

            log.info("Notification {} retry #{} in {}ms (error: {})",
                    notification.getId(), notification.getAttemptCount() + 1, delay, error);
        } else {
            fail(notification, error, notification.getAttemptCount());
        }
    }

    private void fail(Notification notification, String error, int attempts) {
        notification.setStatus(NotificationStatus.FAILED);
        notification.setLastError(error);
        notificationRepository.save(notification);

        callbackService.sendCallback(notification, "FAILED", error);

        log.warn("Notification {} permanently failed after {} attempts: {}",
                notification.getId(), attempts, error);
    }

    private long calculateBackoff(int attemptCount) {
        long delay = (long) (initialDelayMs * Math.pow(backoffMultiplier, attemptCount - 1));
        return Math.min(delay, maxDelayMs);
    }

    private void recoverStuck() {
        // Find notifications stuck in PROCESSING for more than 5 minutes
        // In production, this would use a database query
        log.debug("Checking for stuck notifications...");
    }
}
