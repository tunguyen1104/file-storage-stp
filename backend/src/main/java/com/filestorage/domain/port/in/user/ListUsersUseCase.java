package com.filestorage.domain.port.in.user;

import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.user.UserPageView;

import java.util.UUID;

public interface ListUsersUseCase {

    UserPageView execute(ListUsersQuery query);

    record ListUsersQuery(
            UUID actorUserId,
            UUID actorTenantId,
            RoleCode actorRole,
            UUID tenantId,
            Boolean isActive,
            RoleCode roleCode,
            String search,
            int page,
            int limit
    ) {
    }
}

