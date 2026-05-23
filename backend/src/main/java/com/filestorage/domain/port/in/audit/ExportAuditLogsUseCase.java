package com.filestorage.domain.port.in.audit;

import com.filestorage.domain.model.audit.AuditAction;
import com.filestorage.domain.model.audit.AuditLogSortBy;
import com.filestorage.domain.model.audit.AuditLogView;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.enums.SortDirection;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface ExportAuditLogsUseCase {

    List<AuditLogView> execute(ExportAuditLogsQuery query);

    record ExportAuditLogsQuery(
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
            AuditLogSortBy sortBy,
            SortDirection sortDirection
    ) {
    }
}
