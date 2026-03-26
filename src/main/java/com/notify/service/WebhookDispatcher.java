package com.notify.service;

import com.notify.entity.Notification;
import com.notify.enums.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Webhook channel dispatcher.
 * Posts notification payload to a callback URL.
 */
@Component
@Slf4j
public class WebhookDispatcher implements ChannelDispatcher {

    private final HttpClient httpClient;

    public WebhookDispatcher(@Value("${notification.webhook.timeout-ms:5000}") int timeoutMs) {
        this.httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofMillis(timeoutMs))
                .followRedirects(HttpClient.Redirect.NORMAL)
                .build();
    }

    @Override
    public Channel getChannel() {
        return Channel.WEBHOOK;
    }

    @Override
    public DeliveryResult deliver(Notification notification) {
        String url = notification.getRecipient();
        long start = System.currentTimeMillis();

        try {
            String payload = String.format(
                    "{\"id\":%d,\"channel\":\"WEBHOOK\",\"body\":\"%s\",\"timestamp\":\"%s\"}",
                    notification.getId(),
                    notification.getBody().replace("\"", "\\\""),
                    notification.getCreatedAt());

            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(url))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(payload))
                    .build();

            HttpResponse<String> response = httpClient.send(request,
                    HttpResponse.BodyHandlers.ofString());

            long duration = System.currentTimeMillis() - start;

            if (response.statusCode() >= 200 && response.statusCode() < 300) {
                log.info("Webhook delivered to {} (HTTP {}, {}ms)", url, response.statusCode(), duration);
                return DeliveryResult.success(
                        "HTTP " + response.statusCode() + " from " + url, duration);
            } else {
                log.warn("Webhook failed to {} (HTTP {}, {}ms)", url, response.statusCode(), duration);
                return DeliveryResult.failure(
                        "HTTP " + response.statusCode() + ": " + response.body(), duration);
            }
        } catch (Exception e) {
            long duration = System.currentTimeMillis() - start;
            log.error("Webhook error for {}: {} ({}ms)", url, e.getMessage(), duration);
            return DeliveryResult.failure("Connection error: " + e.getMessage(), duration);
        }
    }
}
