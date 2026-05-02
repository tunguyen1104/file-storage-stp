package com.filestorage.domain.port.in.file;

import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.file.PartPresignedUrlView;

import java.util.UUID;

public interface GetPartPresignedUrlUseCase {

    PartPresignedUrlView execute(GetPartPresignedUrlCommand command);

    record GetPartPresignedUrlCommand(
            UUID userId,
            UUID tenantId,
            RoleCode role,
            String uploadId,
            int partNumber
    ) {}
}
