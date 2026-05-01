package com.filestorage.domain.port.in.file;

import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.file.FileView;

import java.util.UUID;

public interface RenameFileUseCase {

    FileView execute(RenameFileCommand command);

    record RenameFileCommand(
            UUID actorUserId,
            UUID actorTenantId,
            RoleCode actorRole,
            UUID fileId,
            String originalName
    ) {
    }
}

