package com.filestorage.domain.port.in.file;

import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.file.FileView;

import java.util.UUID;

public interface GetFileMetadataUseCase {

    FileView execute(GetFileMetadataQuery query);

    record GetFileMetadataQuery(
            UUID actorUserId,
            UUID actorTenantId,
            RoleCode actorRole,
            UUID fileId
    ) {
    }
}

