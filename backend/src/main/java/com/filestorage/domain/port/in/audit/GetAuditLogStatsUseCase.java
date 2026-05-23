package com.filestorage.domain.port.in.audit;

import com.filestorage.domain.model.audit.AuditLogStats;
import com.filestorage.domain.model.enums.RoleCode;

import java.util.UUID;

public interface GetAuditLogStatsUseCase {

    AuditLogStats execute(GetAuditLogStatsQuery query);

    record GetAuditLogStatsQuery(
            UUID actorUserId,
            UUID actorTenantId,
            RoleCode actorRole
    ) {
    }
}
