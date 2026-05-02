package com.filestorage.domain.port.in.file;

import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.file.MultipartSessionView;

import java.util.UUID;

public interface GetMultipartUploadSessionUseCase {

    MultipartSessionView execute(GetMultipartUploadSessionQuery query);

    record GetMultipartUploadSessionQuery(
            UUID userId,
            UUID tenantId,
            RoleCode role,
            String uploadId
    ) {}
}
