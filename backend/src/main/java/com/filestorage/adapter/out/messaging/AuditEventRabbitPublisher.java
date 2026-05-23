package com.filestorage.adapter.out.messaging;

import com.filestorage.domain.port.in.audit.LogAuditEventUseCase.AuditEvent;
import com.filestorage.domain.port.out.audit.AuditEventPublisherPort;
import com.filestorage.infrastructure.config.AuditRabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;

/**
 * Outbound RabbitMQ publisher for audit events. The application layer calls
 * {@code AuditLoggerService.logAsync(event)} which delegates here; the consumer
 * ({@code AuditEventConsumer}) persists the row off the request thread.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AuditEventRabbitPublisher implements AuditEventPublisherPort {

    private final RabbitTemplate rabbitTemplate;

    @Override
    public void publish(AuditEvent event) {
        rabbitTemplate.convertAndSend(
                AuditRabbitMQConfig.AUDIT_EXCHANGE,
                AuditRabbitMQConfig.RK_AUDIT_EVENT,
                event
        );
    }
}
