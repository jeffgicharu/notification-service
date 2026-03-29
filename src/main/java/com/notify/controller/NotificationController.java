package com.notify.controller;

import com.notify.dto.request.BulkNotificationRequest;
import com.notify.dto.request.SendNotificationRequest;
import com.notify.dto.response.ApiResponse;
import com.notify.dto.response.NotificationResponse;
import com.notify.dto.response.StatsResponse;
import com.notify.enums.NotificationStatus;
import com.notify.service.NotificationService;
import com.notify.template.TemplateEngine;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
@Tag(name = "Notifications", description = "Send, track, and manage notifications")
public class NotificationController {

    private final NotificationService notificationService;
    private final TemplateEngine templateEngine;

    @PostMapping
    @Operation(summary = "Send a notification", description = "Queue a notification for async delivery")
    public ResponseEntity<ApiResponse<NotificationResponse>> send(
            @Valid @RequestBody SendNotificationRequest request) {
        NotificationResponse response = notificationService.send(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success("Notification queued", response));
    }

    @PostMapping("/bulk")
    @Operation(summary = "Send bulk notifications", description = "Queue notifications to multiple recipients")
    public ResponseEntity<ApiResponse<List<NotificationResponse>>> sendBulk(
            @Valid @RequestBody BulkNotificationRequest request) {
        List<NotificationResponse> responses = notificationService.sendBulk(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED)
                .body(ApiResponse.success(responses.size() + " notifications queued", responses));
    }

    @GetMapping("/{id}")
    @Operation(summary = "Get notification by ID")
    public ResponseEntity<ApiResponse<NotificationResponse>> getById(@PathVariable Long id) {
        return ResponseEntity.ok(
                ApiResponse.success("Retrieved", notificationService.getById(id)));
    }

    @GetMapping("/key/{idempotencyKey}")
    @Operation(summary = "Get notification by idempotency key")
    public ResponseEntity<ApiResponse<NotificationResponse>> getByKey(
            @PathVariable String idempotencyKey) {
        return ResponseEntity.ok(
                ApiResponse.success("Retrieved", notificationService.getByKey(idempotencyKey)));
    }

    @GetMapping("/recipient/{recipient}")
    @Operation(summary = "Get notifications for a recipient")
    public ResponseEntity<ApiResponse<Page<NotificationResponse>>> getByRecipient(
            @PathVariable String recipient,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Retrieved",
                notificationService.getByRecipient(recipient, pageable)));
    }

    @GetMapping("/status/{status}")
    @Operation(summary = "Get notifications by status")
    public ResponseEntity<ApiResponse<Page<NotificationResponse>>> getByStatus(
            @PathVariable NotificationStatus status,
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Retrieved",
                notificationService.getByStatus(status, pageable)));
    }

    @GetMapping("/stats")
    @Operation(summary = "Get delivery statistics")
    public ResponseEntity<ApiResponse<StatsResponse>> getStats() {
        return ResponseEntity.ok(
                ApiResponse.success("Stats retrieved", notificationService.getStats()));
    }

    @GetMapping("/templates")
    @Operation(summary = "List available notification templates")
    public ResponseEntity<ApiResponse<Map<String, TemplateEngine.Template>>> getTemplates() {
        return ResponseEntity.ok(
                ApiResponse.success("Templates retrieved", templateEngine.getAll()));
    }

    @GetMapping("/{id}/delivery-logs")
    @Operation(summary = "Get delivery attempt logs for a notification")
    public ResponseEntity<ApiResponse<List<com.notify.entity.DeliveryLog>>> getDeliveryLogs(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Delivery logs",
                notificationService.getDeliveryLogs(id)));
    }

    @PostMapping("/{id}/cancel")
    @Operation(summary = "Cancel a queued notification")
    public ResponseEntity<ApiResponse<NotificationResponse>> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Notification cancelled",
                notificationService.cancel(id)));
    }

    @PostMapping("/{id}/resend")
    @Operation(summary = "Resend a failed notification")
    public ResponseEntity<ApiResponse<NotificationResponse>> resend(@PathVariable Long id) {
        return ResponseEntity.ok(ApiResponse.success("Notification re-queued",
                notificationService.resend(id)));
    }

    @GetMapping("/channels/health")
    @Operation(summary = "Delivery health per channel")
    public ResponseEntity<ApiResponse<Map<String, Object>>> channelHealth() {
        return ResponseEntity.ok(ApiResponse.success("Channel health",
                notificationService.getChannelHealth()));
    }

    @GetMapping("/scheduled")
    @Operation(summary = "List pending scheduled notifications")
    public ResponseEntity<ApiResponse<Page<NotificationResponse>>> getScheduled(
            @PageableDefault(size = 20) Pageable pageable) {
        return ResponseEntity.ok(ApiResponse.success("Scheduled notifications",
                notificationService.getScheduled(pageable)));
    }
}
