package com.filestorage.adapter.in.messaging;

import com.filestorage.adapter.out.messaging.event.EmailEvent;
import com.filestorage.application.usecase.email.EmailDispatchService;
import com.filestorage.infrastructure.config.RabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.mail.MailException;
import org.springframework.stereotype.Component;

/**
 * Inbound RabbitMQ adapter — consumes {@link EmailEvent} messages from {@code email.queue}
 * and delegates to {@link EmailDispatchService} for SMTP delivery.
 * <p>
 * On {@link MailException} the exception is re-thrown so Spring AMQP's retry interceptor
 * can attempt up to 3 deliveries before routing the message to the DLQ ({@code email.dlq}).
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class EmailConsumer {

    private final EmailDispatchService emailDispatchService;

    @RabbitListener(queues = RabbitMQConfig.EMAIL_QUEUE)
    public void handleEmailEvent(EmailEvent event) {
        log.debug("Received email event [{}] for {}", event.type(), event.to());
        try {
            emailDispatchService.send(event);
        } catch (MailException e) {
            log.error("SMTP failure for email event [{}] to={}: {} — will retry / route to DLQ",
                    event.type(), event.to(), e.getMessage());
            throw e; // rethrow → triggers retry interceptor → DLQ after exhaustion
        }
    }
}
