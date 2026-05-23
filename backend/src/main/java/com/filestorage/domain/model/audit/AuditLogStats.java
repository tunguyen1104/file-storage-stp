package com.filestorage.domain.model.audit;

import java.util.Map;

public record AuditLogStats(
        long total,
        Map<AuditSeverity, Long> severityCounts
) {
}
