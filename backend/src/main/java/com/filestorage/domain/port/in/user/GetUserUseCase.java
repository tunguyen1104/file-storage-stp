package com.filestorage.domain.port.in.user;

import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.user.UserView;

import java.util.UUID;

public interface GetUserUseCase {

    UserView execute(GetUserQuery query);

    record GetUserQuery(
            UUID targetUserId,
            UUID actorUserId,
            UUID actorTenantId,
            RoleCode actorRole
    ) {
    }
}

