package com.notify.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;

import java.util.Map;

@Data
@Builder
@AllArgsConstructor
public class StatsResponse {
    private long totalNotifications;
    private long last24Hours;
    private long deliveredLast24Hours;
    private double deliveryRateLast24Hours;
    private Map<String, Long> byStatus;
    private Map<String, Long> byChannel;
    private int queueSize;
    private int activeWorkers;
}
