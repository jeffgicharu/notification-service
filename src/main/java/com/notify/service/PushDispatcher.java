package com.notify.service;

import com.notify.entity.Notification;
import com.notify.enums.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Random;

/**
 * Push notification dispatcher.
 * In production, this would use Firebase Cloud Messaging (FCM) or APNs.
 */
@Component
@Slf4j
public class PushDispatcher implements ChannelDispatcher {

    private final Random random = new Random();

    @Override
    public Channel getChannel() {
        return Channel.PUSH;
    }

    @Override
    public DeliveryResult deliver(Notification notification) {
        long start = System.currentTimeMillis();

        try {
            Thread.sleep(30 + random.nextInt(150));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        long duration = System.currentTimeMillis() - start;

        if (random.nextInt(10) < 9) {
            log.info("Push delivered to {} ({}ms)", notification.getRecipient(), duration);
            return DeliveryResult.success("Delivered via FCM", duration);
        } else {
            return DeliveryResult.failure("FCM error: invalid registration token", duration);
        }
    }
}
