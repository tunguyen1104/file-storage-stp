package com.filestorage.domain.port.in.file;

import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.file.FilePresignedGetView;

import java.util.UUID;

public interface GetFilePresignedUrlUseCase {

    FilePresignedGetView execute(GetFilePresignedUrlQuery query);

    record GetFilePresignedUrlQuery(
            UUID actorUserId,
            UUID actorTenantId,
            RoleCode actorRole,
            UUID fileId,
            Integer ttlMinutes
    ) {
    }
}

