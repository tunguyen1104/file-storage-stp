package com.filestorage.domain.port.in.file;

import com.filestorage.domain.model.enums.RoleCode;

import java.util.UUID;

public interface AbortMultipartUploadUseCase {

    AbortView execute(AbortMultipartUploadCommand command);

    record AbortMultipartUploadCommand(
            UUID userId,
            UUID tenantId,
            RoleCode role,
            String uploadId
    ) {}

    record AbortView(String uploadId, String status) {}
}
