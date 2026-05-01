package com.filestorage.domain.port.in.folder;

import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.folder.FolderView;

import java.util.UUID;

public interface RenameFolderUseCase {

    FolderView execute(RenameFolderCommand command);

    record RenameFolderCommand(
            UUID actorUserId,
            UUID actorTenantId,
            RoleCode actorRole,
            UUID folderId,
            String name
    ) {
    }
}

