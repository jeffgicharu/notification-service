package com.notify.queue;

import com.notify.entity.Notification;
import com.notify.enums.Priority;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.concurrent.PriorityBlockingQueue;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-memory priority queue for notification dispatch.
 * CRITICAL > HIGH > NORMAL > LOW, then FIFO within same priority.
 * <p>
 * In production, this would be backed by Kafka, RabbitMQ, or Redis Streams.
 */
@Component
@Slf4j
public class NotificationQueue {

    private final PriorityBlockingQueue<QueueEntry> queue;
    private final AtomicInteger sequence = new AtomicInteger(0);

    public NotificationQueue(@Value("${notification.queue.capacity:10000}") int capacity) {
        this.queue = new PriorityBlockingQueue<>(capacity,
                Comparator.comparingInt((QueueEntry e) -> e.priorityRank())
                        .thenComparingInt(QueueEntry::sequence));
    }

    public void enqueue(Notification notification) {
        int rank = priorityToRank(notification.getPriority());
        int seq = sequence.incrementAndGet();
        queue.offer(new QueueEntry(notification.getId(), rank, seq));
        log.debug("Enqueued notification {} (priority: {}, queue size: {})",
                notification.getId(), notification.getPriority(), queue.size());
    }

    public Long poll() {
        QueueEntry entry = queue.poll();
        return entry != null ? entry.notificationId() : null;
    }

    public int size() {
        return queue.size();
    }

    public boolean isEmpty() {
        return queue.isEmpty();
    }

    private int priorityToRank(Priority priority) {
        return switch (priority) {
            case CRITICAL -> 0;
            case HIGH -> 1;
            case NORMAL -> 2;
            case LOW -> 3;
        };
    }

    private record QueueEntry(Long notificationId, int priorityRank, int sequence) {}
}
