package com.filestorage.domain.port.in.file;

import com.filestorage.domain.model.file.PartCompletionView;

import java.util.UUID;

public interface CompletePartUploadUseCase {

    PartCompletionView execute(CompletePartUploadCommand command);

    record CompletePartUploadCommand(
            UUID userId,
            UUID tenantId,
            String uploadId,
            int partNumber,
            String etag
    ) {}
}
