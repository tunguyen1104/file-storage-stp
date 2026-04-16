package com.filestorage.domain.port.in.tenant;

import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.tenant.TenantView;

import java.util.UUID;

public interface UpdateTenantUseCase {

    TenantView execute(UpdateTenantCommand command);

    record UpdateTenantCommand(
            UUID actorUserId,
            RoleCode actorRole,
            UUID tenantId,
            String name,
            String slug,
            Long storageQuotaBytes
    ) {
    }
}

