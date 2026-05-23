package com.filestorage.domain.model.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public record AuditLogView(
        UUID id,
        UUID tenantId,
        UUID userId,
        String userEmail,
        UUID fileId,
        AuditAction action,
        String actorRole,
        String ipAddress,
        String userAgent,
        String requestId,
        Map<String, Object> extraData,
        Instant createdAt
) {
}
