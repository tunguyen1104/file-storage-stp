package com.filestorage.domain.port.in.file;

import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.file.MultipartUploadInitView;

import java.util.UUID;

public interface InitMultipartUploadUseCase {

    MultipartUploadInitView execute(InitMultipartUploadCommand command);

    record InitMultipartUploadCommand(
            UUID userId,
            UUID tenantId,
            RoleCode role,
            UUID folderId,
            String originalName,
            String mimeType,
            long totalSizeBytes,
            int totalParts
    ) {}
}
