package com.filestorage.domain.port.in.file;

import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.file.MultipartCompleteView;

import java.util.List;
import java.util.UUID;

public interface CompleteMultipartUploadUseCase {

    MultipartCompleteView execute(CompleteMultipartUploadCommand command);

    record CompleteMultipartUploadCommand(
            UUID userId,
            UUID tenantId,
            RoleCode role,
            String uploadId,
            List<PartInfo> parts
    ) {}

    /**
     * Part descriptor sent by the client when completing the multipart upload.
     */
    record PartInfo(int partNumber, String etag) {}
}
