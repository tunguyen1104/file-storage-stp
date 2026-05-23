package com.filestorage.adapter.in.rest.dto.response.audit;

import com.filestorage.domain.model.audit.AuditSeverity;

import java.util.Map;

public record AuditLogStatsResponse(
        long total,
        Map<AuditSeverity, Long> severityCounts
) {
}
