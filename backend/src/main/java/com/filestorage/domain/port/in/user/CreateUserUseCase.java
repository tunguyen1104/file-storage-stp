package com.filestorage.domain.port.in.user;

import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.user.UserView;

import java.util.UUID;

public interface CreateUserUseCase {

    UserView execute(CreateUserCommand command);

    record CreateUserCommand(
            UUID actorUserId,
            UUID actorTenantId,
            RoleCode actorRole,
            String email,
            String fullName,
            UUID roleId,
            Long storageQuotaBytes,
            UUID tenantId
    ) {
    }
}

