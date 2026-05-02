package com.filestorage.domain.port.in.file;

import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.file.PresignedUploadView;

import java.util.UUID;

public interface InitPresignedUploadUseCase {

    PresignedUploadView execute(InitPresignedUploadCommand command);

    record InitPresignedUploadCommand(
            UUID userId,
            UUID tenantId,
            RoleCode role,
            UUID folderId,
            String originalName,
            String mimeType,
            long sizeBytes
    ) {
    }
}
