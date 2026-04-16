package com.filestorage.domain.port.in.user;

import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.user.UserView;

import java.util.UUID;

public interface DeactivateUserUseCase {

    UserView execute(DeactivateUserCommand command);

    record DeactivateUserCommand(
            UUID actorUserId,
            UUID actorTenantId,
            RoleCode actorRole,
            UUID targetUserId
    ) {
    }
}

