package com.filestorage.domain.port.in.folder;

import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.folder.FolderView;

import java.util.UUID;

public interface CreateFolderUseCase {

    FolderView execute(CreateFolderCommand command);

    record CreateFolderCommand(
            UUID actorUserId,
            UUID actorTenantId,
            RoleCode actorRole,
            String name,
            UUID parentId
    ) {
    }
}

