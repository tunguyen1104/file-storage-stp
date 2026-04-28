package com.filestorage.domain.port.in.file;

import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.file.FileView;

import java.util.List;
import java.util.UUID;

public interface BulkUploadFilesUseCase {

    List<FileView> execute(BulkUploadCommand command);

    record BulkUploadCommand(
            UUID actorUserId,
            UUID actorTenantId,
            RoleCode actorRole,
            UUID folderId,
            List<UploadItem> items
    ) {
    }

    record UploadItem(
            String originalName,
            String contentType,
            byte[] content
    ) {
    }
}

