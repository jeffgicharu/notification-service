package com.notify.service;

import com.notify.entity.Notification;
import com.notify.enums.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Random;

/**
 * SMS channel dispatcher.
 * In production, this would integrate with Africa's Talking, Twilio, or a telco SMPP gateway.
 * For simulation, it succeeds ~90% of the time with realistic latency.
 */
@Component
@Slf4j
public class SmsDispatcher implements ChannelDispatcher {

    private final Random random = new Random();

    @Override
    public Channel getChannel() {
        return Channel.SMS;
    }

    @Override
    public DeliveryResult deliver(Notification notification) {
        long start = System.currentTimeMillis();

        // Simulate network latency (50-300ms)
        try {
            Thread.sleep(50 + random.nextInt(250));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        long duration = System.currentTimeMillis() - start;

        // Simulate 90% success rate
        if (random.nextInt(10) < 9) {
            String messageId = "SMS-" + System.currentTimeMillis();
            log.info("SMS delivered to {} [{}] ({}ms)", notification.getRecipient(), messageId, duration);
            return DeliveryResult.success("Delivered via SMPP gateway. MessageId: " + messageId, duration);
        } else {
            log.warn("SMS delivery failed to {} ({}ms)", notification.getRecipient(), duration);
            return DeliveryResult.failure("SMPP error: temporary network failure", duration);
        }
    }
}
