package com.filestorage.domain.model.audit;

import java.util.List;

public record AuditLogPage(
        List<AuditLogView> data,
        int currentPage,
        int pageSize,
        long totalElements,
        int totalPages
) {
}
