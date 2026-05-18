package com.filestorage.domain.port.in.tenant;

import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.tenant.TenantView;

import java.util.UUID;

public interface ActivateTenantUseCase {

    TenantView execute(ActivateTenantCommand command);

    record ActivateTenantCommand(
            UUID actorUserId,
            RoleCode actorRole,
            UUID tenantId
    ) {
    }
}
