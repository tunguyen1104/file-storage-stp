package com.filestorage.domain.port.in.tenant;

import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.tenant.TenantView;

import java.util.UUID;

public interface GetTenantUseCase {

    TenantView execute(GetTenantQuery query);

    record GetTenantQuery(
            UUID actorUserId,
            RoleCode actorRole,
            UUID tenantId
    ) {
    }
}

