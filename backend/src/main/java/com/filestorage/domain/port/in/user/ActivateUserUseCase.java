package com.filestorage.domain.port.in.user;

import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.user.UserView;

import java.util.UUID;

public interface ActivateUserUseCase {

    UserView execute(ActivateUserCommand command);

    record ActivateUserCommand(
            UUID actorUserId,
            UUID actorTenantId,
            RoleCode actorRole,
            UUID targetUserId
    ) {
    }
}
