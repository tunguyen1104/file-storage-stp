package com.filestorage.domain.port.in.user;

import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.user.UserSearchResult;

import java.util.List;
import java.util.UUID;

public interface SearchUsersUseCase {

    List<UserSearchResult> execute(SearchUsersQuery query);

    record SearchUsersQuery(
            UUID actorUserId,
            UUID actorTenantId,
            RoleCode actorRole,
            String email,
            UUID tenantId
    ) {
    }
}
