package com.filestorage.adapter.in.rest.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Positive;

import java.util.UUID;

public record PresignedUploadInitRequest(
        @NotBlank String originalName,
        @NotBlank String mimeType,
        @Positive long sizeBytes,
        UUID folderId
) {
}
