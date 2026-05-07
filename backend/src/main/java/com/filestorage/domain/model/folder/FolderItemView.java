package com.filestorage.domain.model.folder;

import com.filestorage.domain.model.enums.FileStatus;
import com.filestorage.domain.model.enums.FolderItemType;

import java.time.Instant;
import java.util.UUID;

public record FolderItemView(
        UUID id,
        FolderItemType type,
        String name,
        UUID parentId,
        long sizeBytes,
        String mimeType,
        FileStatus status,
        UUID actorId,
        String actorFullName,
        Instant createdAt,
        Instant updatedAt
) {
}
