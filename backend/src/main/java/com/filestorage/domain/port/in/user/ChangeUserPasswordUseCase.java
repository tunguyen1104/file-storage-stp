package com.filestorage.domain.port.in.user;

import com.filestorage.domain.model.enums.RoleCode;

import java.util.UUID;

public interface ChangeUserPasswordUseCase {

    void execute(ChangeUserPasswordCommand command);

    record ChangeUserPasswordCommand(
            UUID actorUserId,
            UUID actorTenantId,
            RoleCode actorRole,
            UUID targetUserId,
            String currentPassword,
            String newPassword,
            String confirmPassword
    ) {
    }
}

