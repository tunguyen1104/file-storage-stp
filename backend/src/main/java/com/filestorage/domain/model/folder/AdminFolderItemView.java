package com.filestorage.domain.model.folder;

import com.filestorage.domain.model.enums.FileStatus;
import com.filestorage.domain.model.enums.FolderItemType;

import java.time.Instant;
import java.util.UUID;

public record AdminFolderItemView(
        UUID id,
        FolderItemType type,
        String name,
        UUID tenantId,
        String tenantName,
        UUID parentId,
        String parentName,
        long sizeBytes,
        String mimeType,
        FileStatus status,
        UUID actorId,
        String actorFullName,
        Instant createdAt,
        Instant updatedAt
) {
}
