package com.filestorage.application.usecase.audit;

import com.filestorage.domain.port.in.audit.LogAuditEventUseCase;
import com.filestorage.domain.port.out.audit.AuditEventPublisherPort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Application-layer entry point for the AOP aspect and manual callers. Forwards
 * the event to RabbitMQ via {@link AuditEventPublisherPort}; persistence happens
 * in {@code AuditEventConsumer}. Publish failures are swallowed — a broken
 * broker must never break a business request. The DLQ + retry interceptor
 * (see {@code AuditRabbitMQConfig}) cover transient broker hiccups.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class AuditLoggerService implements LogAuditEventUseCase {

    private final AuditEventPublisherPort publisher;

    @Override
    public void logAsync(AuditEvent event) {
        try {
            publisher.publish(event);
        } catch (Exception ex) {
            log.warn("audit_log_publish_failed action={} tenantId={} userId={} fileId={}: {}",
                    event.action(), event.tenantId(), event.userId(), event.fileId(), ex.getMessage());
        }
    }
}
