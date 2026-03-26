package com.notify.dto.request;

import com.notify.enums.Channel;
import com.notify.enums.Priority;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

import java.util.List;
import java.util.Map;

@Data
public class BulkNotificationRequest {

    @NotNull(message = "Channel is required")
    private Channel channel;

    @NotEmpty(message = "At least one recipient is required")
    private List<String> recipients;

    private String subject;

    private String body;

    private String templateId;

    private Map<String, String> templateParams;

    private Priority priority;

    @NotBlank(message = "Batch ID is required")
    private String batchId;
}
