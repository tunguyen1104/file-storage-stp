package com.filestorage.domain.port.in.user;

import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.user.UserStorageView;

import java.util.UUID;

public interface GetUserStorageUseCase {

    UserStorageView execute(GetUserStorageQuery query);

    record GetUserStorageQuery(
            UUID targetUserId,
            UUID actorUserId,
            UUID actorTenantId,
            RoleCode actorRole
    ) {
    }
}

