package com.filestorage.adapter.out.persistence.adapter;

import com.filestorage.adapter.out.persistence.entity.AuditLog;
import com.filestorage.adapter.out.persistence.repository.AuditLogJpaRepository;
import com.filestorage.domain.port.in.audit.LogAuditEventUseCase.AuditEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Persists an {@link AuditEvent} into {@code audit_logs}. Called from
 * {@code AuditEventConsumer} — never directly from the application layer.
 */
@Component
@RequiredArgsConstructor
public class AuditLogPersister {

    private final AuditLogJpaRepository auditLogRepository;

    @Transactional
    public void persist(AuditEvent event) {
        AuditLog entity = AuditLog.builder()
                .tenantId(event.tenantId())
                .userId(event.userId())
                .fileId(event.fileId())
                .action(event.action())
                .ipAddress(event.ipAddress())
                .userAgent(event.userAgent())
                .extraData(event.extraData())
                .build();
        auditLogRepository.save(entity);
    }
}
