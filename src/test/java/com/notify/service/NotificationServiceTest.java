package com.notify.service;

import com.notify.dto.request.BulkNotificationRequest;
import com.notify.dto.request.SendNotificationRequest;
import com.notify.dto.response.NotificationResponse;
import com.notify.dto.response.StatsResponse;
import com.notify.enums.Channel;
import com.notify.enums.NotificationStatus;
import com.notify.enums.Priority;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest
class NotificationServiceTest {

    @Autowired
    private NotificationService notificationService;

    @Test
    @DisplayName("Should queue an SMS notification")
    void send_sms_queuesSuccessfully() {
        SendNotificationRequest request = new SendNotificationRequest();
        request.setChannel(Channel.SMS);
        request.setRecipient("+254700000001");
        request.setBody("Test SMS message");
        request.setIdempotencyKey("test-sms-001");

        NotificationResponse response = notificationService.send(request);

        assertNotNull(response.getId());
        assertEquals(Channel.SMS, response.getChannel());
        assertEquals("+254700000001", response.getRecipient());
        assertEquals(NotificationStatus.QUEUED, response.getStatus());
        assertEquals(Priority.NORMAL, response.getPriority());
    }

    @Test
    @DisplayName("Should queue an email notification with subject")
    void send_email_withSubject() {
        SendNotificationRequest request = new SendNotificationRequest();
        request.setChannel(Channel.EMAIL);
        request.setRecipient("user@example.com");
        request.setSubject("Test Subject");
        request.setBody("Test email body");
        request.setIdempotencyKey("test-email-001");
        request.setPriority(Priority.HIGH);

        NotificationResponse response = notificationService.send(request);

        assertEquals(Channel.EMAIL, response.getChannel());
        assertEquals("Test Subject", response.getSubject());
        assertEquals(Priority.HIGH, response.getPriority());
    }

    @Test
    @DisplayName("Should resolve template and queue notification")
    void send_withTemplate_resolvesCorrectly() {
        SendNotificationRequest request = new SendNotificationRequest();
        request.setChannel(Channel.SMS);
        request.setRecipient("+254700000002");
        request.setTemplateId("otp");
        request.setTemplateParams(Map.of("code", "482917", "expiry", "5"));
        request.setIdempotencyKey("test-template-001");

        NotificationResponse response = notificationService.send(request);

        assertNotNull(response.getId());
        assertEquals(NotificationStatus.QUEUED, response.getStatus());
    }

    @Test
    @DisplayName("Should reject unknown template")
    void send_unknownTemplate_throws() {
        SendNotificationRequest request = new SendNotificationRequest();
        request.setChannel(Channel.SMS);
        request.setRecipient("+254700000001");
        request.setTemplateId("nonexistent");
        request.setIdempotencyKey("test-bad-template");

        assertThrows(IllegalArgumentException.class,
                () -> notificationService.send(request));
    }

    @Test
    @DisplayName("Should reject duplicate idempotency key with same response")
    void send_duplicate_returnsSameResponse() {
        SendNotificationRequest request = new SendNotificationRequest();
        request.setChannel(Channel.SMS);
        request.setRecipient("+254700000001");
        request.setBody("Duplicate test");
        request.setIdempotencyKey("test-dup-001");

        NotificationResponse first = notificationService.send(request);
        NotificationResponse second = notificationService.send(request);

        assertEquals(first.getId(), second.getId());
    }

    @Test
    @DisplayName("Should queue bulk notifications")
    void sendBulk_queuesAll() {
        BulkNotificationRequest request = new BulkNotificationRequest();
        request.setChannel(Channel.SMS);
        request.setRecipients(List.of("+254700000001", "+254700000002", "+254700000003"));
        request.setBody("Bulk message");
        request.setBatchId("batch-001");

        List<NotificationResponse> responses = notificationService.sendBulk(request);

        assertEquals(3, responses.size());
        responses.forEach(r -> assertEquals(NotificationStatus.QUEUED, r.getStatus()));
    }

    @Test
    @DisplayName("Should retrieve notification by ID")
    void getById_returnsNotification() {
        SendNotificationRequest request = new SendNotificationRequest();
        request.setChannel(Channel.PUSH);
        request.setRecipient("device-token-123");
        request.setBody("Push test");
        request.setIdempotencyKey("test-get-001");

        NotificationResponse created = notificationService.send(request);
        NotificationResponse retrieved = notificationService.getById(created.getId());

        assertEquals(created.getId(), retrieved.getId());
        assertEquals("device-token-123", retrieved.getRecipient());
    }

    @Test
    @DisplayName("Should retrieve notification by idempotency key")
    void getByKey_returnsNotification() {
        SendNotificationRequest request = new SendNotificationRequest();
        request.setChannel(Channel.SMS);
        request.setRecipient("+254700000001");
        request.setBody("Key lookup test");
        request.setIdempotencyKey("test-key-lookup");

        notificationService.send(request);
        NotificationResponse retrieved = notificationService.getByKey("test-key-lookup");

        assertEquals("test-key-lookup", retrieved.getIdempotencyKey());
    }

    @Test
    @DisplayName("Should return stats")
    void getStats_returnsValidStats() {
        // Send a few notifications first
        for (int i = 0; i < 3; i++) {
            SendNotificationRequest req = new SendNotificationRequest();
            req.setChannel(Channel.SMS);
            req.setRecipient("+254700000001");
            req.setBody("Stats test " + i);
            req.setIdempotencyKey("test-stats-" + i);
            notificationService.send(req);
        }

        StatsResponse stats = notificationService.getStats();

        assertTrue(stats.getTotalNotifications() >= 3);
        assertNotNull(stats.getByStatus());
        assertNotNull(stats.getByChannel());
    }

    @Test
    @DisplayName("Should handle priority correctly")
    void send_criticalPriority() {
        SendNotificationRequest request = new SendNotificationRequest();
        request.setChannel(Channel.SMS);
        request.setRecipient("+254700000001");
        request.setBody("Critical alert");
        request.setPriority(Priority.CRITICAL);
        request.setIdempotencyKey("test-critical-001");

        NotificationResponse response = notificationService.send(request);

        assertEquals(Priority.CRITICAL, response.getPriority());
    }

    @Test
    @DisplayName("Should reject notification without body or template")
    void send_noBodyNoTemplate_throws() {
        SendNotificationRequest request = new SendNotificationRequest();
        request.setChannel(Channel.SMS);
        request.setRecipient("+254700000001");
        request.setIdempotencyKey("test-nobody");

        assertThrows(IllegalArgumentException.class,
                () -> notificationService.send(request));
    }
}
