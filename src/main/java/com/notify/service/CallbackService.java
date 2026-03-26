package com.notify.service;

import com.notify.entity.Notification;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Sends delivery status callbacks to the caller's webhook URL.
 */
@Service
@Slf4j
public class CallbackService {

    private final HttpClient httpClient;
    private final boolean enabled;

    public CallbackService(
            @Value("${notification.webhook.enabled:true}") boolean enabled,
            @Value("${notification.webhook.timeout-ms:5000}") int timeoutMs) {
        this.enabled = enabled;
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .build();
    }

    public void sendCallback(Notification notification, String status, String detail) {
        if (!enabled || notification.getCallbackUrl() == null) return;

        try {
            String payload = String.format(
                    "{\"notificationId\":%d,\"idempotencyKey\":\"%s\",\"channel\":\"%s\"," +
                    "\"recipient\":\"%s\",\"status\":\"%s\",\"detail\":\"%s\"," +
                    "\"attemptCount\":%d,\"timestamp\":\"%s\"}",
                    notification.getId(),
                    notification.getIdempotencyKey(),
                    notification.getChannel(),
                    notification.getRecipient(),
                    status,
                    detail.replace("\"", "\\\""),
                    notification.getAttemptCount(),
                    java.time.LocalDateTime.now());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(notification.getCallbackUrl()))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            httpClient.sendAsync(request, HttpResponse.BodyHandlers.ofString())
                    .thenAccept(resp -> log.debug("Callback sent to {} (HTTP {})",
                            notification.getCallbackUrl(), resp.statusCode()))
                    .exceptionally(e -> {
                        log.warn("Callback failed for {}: {}",
                                notification.getCallbackUrl(), e.getMessage());
                        return null;
                    });
        } catch (Exception e) {
            log.warn("Callback error: {}", e.getMessage());
        }
    }
}
