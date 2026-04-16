package com.filestorage.domain.port.in.tenant;

import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.tenant.TenantPageView;

import java.util.UUID;

public interface ListTenantsUseCase {

    TenantPageView execute(ListTenantsQuery query);

    record ListTenantsQuery(
            UUID actorUserId,
            RoleCode actorRole,
            Boolean isActive,
            String search,
            int page,
            int limit
    ) {
    }
}

