package com.filestorage.domain.model.file;

import com.filestorage.domain.model.enums.FileStatus;

import java.util.UUID;

public record ConfirmUploadView(
        UUID fileId,
        FileStatus status
) {
}
