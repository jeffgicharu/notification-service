package com.notify.service;

import com.notify.entity.Notification;
import com.notify.enums.Channel;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Random;

/**
 * Email channel dispatcher.
 * In production, this would use JavaMail/SendGrid/SES.
 */
@Component
@Slf4j
public class EmailDispatcher implements ChannelDispatcher {

    private final Random random = new Random();

    @Override
    public Channel getChannel() {
        return Channel.EMAIL;
    }

    @Override
    public DeliveryResult deliver(Notification notification) {
        long start = System.currentTimeMillis();

        try {
            Thread.sleep(100 + random.nextInt(400));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        long duration = System.currentTimeMillis() - start;

        if (random.nextInt(10) < 9) {
            String messageId = "EMAIL-" + System.currentTimeMillis();
            log.info("Email delivered to {} [{}] ({}ms)", notification.getRecipient(), messageId, duration);
            return DeliveryResult.success("Delivered via SMTP. MessageId: " + messageId, duration);
        } else {
            return DeliveryResult.failure("SMTP error: connection timeout", duration);
        }
    }
}
