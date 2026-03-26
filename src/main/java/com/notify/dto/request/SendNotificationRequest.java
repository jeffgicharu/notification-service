package com.notify.dto.request;

import com.notify.enums.Channel;
import com.notify.enums.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.Map;

@Data
public class SendNotificationRequest {

    @NotNull(message = "Channel is required")
    private Channel channel;

    @NotBlank(message = "Recipient is required")
    private String recipient;

    private String subject;

    private String body;

    private String templateId;

    private Map<String, String> templateParams;

    private Priority priority;

    @NotBlank(message = "Idempotency key is required")
    private String idempotencyKey;

    private String callbackUrl;

    private LocalDateTime scheduledAt;
}
