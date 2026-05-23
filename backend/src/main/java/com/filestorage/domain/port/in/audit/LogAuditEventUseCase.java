package com.filestorage.domain.port.in.audit;

import com.filestorage.domain.model.audit.AuditAction;

import java.util.Map;
import java.util.UUID;

public interface LogAuditEventUseCase {

    void logAsync(AuditEvent event);

    record AuditEvent(
            AuditAction action,
            UUID tenantId,
            UUID userId,
            UUID fileId,
            String actorRole,
            String ipAddress,
            String userAgent,
            String requestId,
            Map<String, Object> extraData
    ) {
    }
}
