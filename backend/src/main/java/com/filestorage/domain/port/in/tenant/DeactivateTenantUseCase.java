package com.filestorage.domain.port.in.tenant;

import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.tenant.TenantView;

import java.util.UUID;

public interface DeactivateTenantUseCase {

    TenantView execute(DeactivateTenantCommand command);

    record DeactivateTenantCommand(
            UUID actorUserId,
            RoleCode actorRole,
            UUID tenantId
    ) {
    }
}

