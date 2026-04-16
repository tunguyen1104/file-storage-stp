package com.filestorage.domain.port.in.tenant;

import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.tenant.TenantView;

import java.util.UUID;

public interface CreateTenantUseCase {

    TenantView execute(CreateTenantCommand command);

    record CreateTenantCommand(
            UUID actorUserId,
            RoleCode actorRole,
            String name,
            String slug,
            Long storageQuotaBytes
    ) {
    }
}

