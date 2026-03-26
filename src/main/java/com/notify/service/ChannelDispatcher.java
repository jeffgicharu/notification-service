package com.notify.service;

import com.notify.entity.Notification;
import com.notify.enums.Channel;

/**
 * Interface for channel-specific delivery.
 * Each channel (SMS, Email, Push, Webhook) implements this.
 */
public interface ChannelDispatcher {

    Channel getChannel();

    /**
     * Attempt to deliver a notification.
     *
     * @return delivery result with success/failure details
     */
    DeliveryResult deliver(Notification notification);

    record DeliveryResult(boolean success, String detail, long durationMs) {
        public static DeliveryResult success(String detail, long durationMs) {
            return new DeliveryResult(true, detail, durationMs);
        }

        public static DeliveryResult failure(String detail, long durationMs) {
            return new DeliveryResult(false, detail, durationMs);
        }
    }
}
