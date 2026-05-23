package com.filestorage.adapter.in.messaging;

import com.filestorage.adapter.out.persistence.adapter.AuditLogPersister;
import com.filestorage.domain.port.in.audit.LogAuditEventUseCase.AuditEvent;
import com.filestorage.infrastructure.config.AuditRabbitMQConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

/**
 * Inbound RabbitMQ adapter — drains {@code audit.queue} and writes rows to
 * {@code audit_logs}. Exhausted retries go to {@code audit.dlq} (see
 * {@link AuditRabbitMQConfig}).
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class AuditEventConsumer {

    private final AuditLogPersister persister;

    @RabbitListener(queues = AuditRabbitMQConfig.AUDIT_QUEUE,
            containerFactory = AuditRabbitMQConfig.LISTENER_FACTORY)
    public void onAuditEvent(AuditEvent event) {
        log.debug("AuditEventConsumer: action={} tenantId={} userId={} fileId={}",
                event.action(), event.tenantId(), event.userId(), event.fileId());
        persister.persist(event);
    }
}
