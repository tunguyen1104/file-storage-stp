package com.filestorage.adapter.out.messaging;

import com.filestorage.adapter.out.messaging.event.EmailEvent;
import com.filestorage.infrastructure.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes email events to RabbitMQ.
 * The actual email delivery is handled by a separate email micro-service
 * that consumes from the {@code email.queue} queue.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class EmailEventPublisher {

    private final RabbitTemplate rabbitTemplate;

    public void publishResetPassword(EmailEvent event) {
        publish(RabbitMQConfig.EMAIL_ROUTING_KEY_RESET, event);
    }

    public void publishVerifyEmail(EmailEvent event) {
        publish(RabbitMQConfig.EMAIL_ROUTING_KEY_VERIFY, event);
    }

    public void publishInitialPassword(EmailEvent event) {
        publish(RabbitMQConfig.EMAIL_ROUTING_KEY_INITIAL, event);
    }

    private void publish(String routingKey, EmailEvent event) {
        try {
            rabbitTemplate.convertAndSend(
                    RabbitMQConfig.EMAIL_EXCHANGE,
                    routingKey,
                    event
            );
            log.info("Published email event [{}] to={} routingKey={}",
                    event.type(), event.to(), routingKey);
        } catch (Exception e) {
            log.error("Failed to publish email event [{}] to={}: {}",
                    event.type(), event.to(), e.getMessage());
            // Fail silently — password-reset Redis key is still stored.
            // A retry mechanism or dead-letter queue can handle this.
        }
    }
}
