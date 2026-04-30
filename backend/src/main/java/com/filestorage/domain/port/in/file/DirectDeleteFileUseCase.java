package com.filestorage.domain.port.in.file;

import com.filestorage.domain.model.enums.RoleCode;

import java.util.UUID;

public interface DirectDeleteFileUseCase {

    void execute(DirectDeleteFileCommand command);

    record DirectDeleteFileCommand(
            UUID actorUserId,
            UUID actorTenantId,
            RoleCode actorRole,
            UUID fileId
    ) {
    }
}

