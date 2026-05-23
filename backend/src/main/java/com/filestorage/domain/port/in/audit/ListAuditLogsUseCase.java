package com.filestorage.domain.port.in.audit;

import com.filestorage.domain.model.audit.AuditAction;
import com.filestorage.domain.model.audit.AuditLogPage;
import com.filestorage.domain.model.audit.AuditLogSortBy;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.enums.SortDirection;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ListAuditLogsUseCase {

    AuditLogPage execute(ListAuditLogsQuery query);

    record ListAuditLogsQuery(
            UUID actorUserId,
            UUID actorTenantId,
            RoleCode actorRole,
            String userEmail,
            List<AuditAction> actions,
            UUID fileId,
            UUID tenantId,
            Instant from,
            Instant to,
            String ipAddress,
            String userAgent,
            int page,
            int limit,
            AuditLogSortBy sortBy,
            SortDirection sortDirection
    ) {
    }
}
