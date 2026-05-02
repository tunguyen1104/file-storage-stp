package com.filestorage.adapter.in.rest.dto.response;

import com.filestorage.domain.model.enums.FileStatus;

import java.util.UUID;

public record ConfirmUploadResponse(
        UUID fileId,
        FileStatus status,
        String message
) {
}
