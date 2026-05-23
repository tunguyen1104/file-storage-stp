package com.filestorage.adapter.in.rest.dto.response.audit;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.filestorage.domain.model.audit.AuditAction;
import com.filestorage.domain.model.audit.AuditSeverity;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record AuditLogResponse(
        UUID id,
        UUID tenantId,
        UUID userId,
        String userEmail,
        UUID fileId,
        AuditAction action,
        AuditSeverity severity,
        String actorRole,
        String ipAddress,
        String userAgent,
        String requestId,
        Map<String, Object> extraData,
        Instant createdAt
) {
}
