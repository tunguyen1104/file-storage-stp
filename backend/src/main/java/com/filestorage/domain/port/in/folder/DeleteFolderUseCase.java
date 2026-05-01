package com.filestorage.domain.port.in.folder;

import com.filestorage.domain.model.enums.RoleCode;

import java.util.UUID;

public interface DeleteFolderUseCase {

    void execute(DeleteFolderCommand command);

    record DeleteFolderCommand(
            UUID actorUserId,
            UUID actorTenantId,
            RoleCode actorRole,
            UUID folderId
    ) {
    }
}

