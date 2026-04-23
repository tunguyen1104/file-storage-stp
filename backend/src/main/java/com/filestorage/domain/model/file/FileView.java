package com.filestorage.domain.model.file;

import com.filestorage.domain.model.enums.FileStatus;

import java.time.Instant;
import java.util.UUID;

public record FileView(
        UUID id,
        String originalName,
        String mimeType,
        long sizeBytes,
        FileStatus status,
        UUID folderId,
        UUID tenantId,
        String storageKey,
        UUID uploadedById,
        String uploadedByFullName,
        String checksumMd5,
        String thumbnailKey,
        boolean isIndexed,
        Instant deletedAt,
        Instant scheduledDeleteAt,
        Instant createdAt,
        Instant updatedAt
) {
}

