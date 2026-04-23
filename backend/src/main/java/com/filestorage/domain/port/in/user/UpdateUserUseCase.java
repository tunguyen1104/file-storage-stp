package com.filestorage.domain.port.in.user;

import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.user.UserView;

import java.util.UUID;

public interface UpdateUserUseCase {

    UserView execute(UpdateUserCommand command);

    record UpdateUserCommand(
            UUID actorUserId,
            UUID actorTenantId,
            RoleCode actorRole,
            UUID targetUserId,
            String fullName,
            String email,
            RoleCode roleCode,
            Long storageQuotaBytes
    ) {
    }
}

