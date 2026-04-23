package com.filestorage.domain.port.in.user;

import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.user.UserPageView;

import java.util.UUID;

public interface ListTenantUsersUseCase {

    UserPageView execute(ListTenantUsersQuery query);

    record ListTenantUsersQuery(
            UUID actorUserId,
            UUID actorTenantId,
            RoleCode actorRole,
            Boolean isActive,
            RoleCode roleCode,
            String search,
            int page,
            int limit
    ) {
    }
}

