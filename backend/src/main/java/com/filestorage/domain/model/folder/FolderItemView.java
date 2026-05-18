package com.filestorage.domain.model.folder;

import com.filestorage.domain.model.enums.FileStatus;
import com.filestorage.domain.model.enums.FolderItemType;
import com.filestorage.domain.model.enums.MyPermission;
import com.filestorage.domain.model.enums.PermissionSource;

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
        MyPermission myPermission,
        PermissionSource permissionSource,
        Instant createdAt,
        Instant updatedAt
) {
}
