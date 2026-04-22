package com.filestorage.adapter.in.rest.dto.response;

import com.filestorage.domain.model.enums.FileStatus;

import java.time.Instant;
import java.util.UUID;

public record FileMetadataResponse(
        UUID id,
        String originalName,
        String mimeType,
        long sizeBytes,
        FileStatus status,
        UUID folderId,
        UUID tenantId,
        String checksumMd5,
        String thumbnailKey,
        boolean isIndexed,
        UserSummaryResponse uploadedBy,
        Instant deletedAt,
        Instant scheduledDeleteAt,
        Instant createdAt,
        Instant updatedAt
) {
}

