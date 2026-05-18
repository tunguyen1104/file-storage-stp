package com.filestorage.adapter.in.rest.dto.response;

import com.filestorage.domain.model.enums.FileStatus;
import com.filestorage.domain.model.enums.MyPermission;
import com.filestorage.domain.model.enums.PermissionSource;

import java.time.Instant;
import java.util.UUID;

public record FolderItemResponse(
        UUID id,
        String type,
        String name,
        UUID parentId,
        long sizeBytes,
        String mimeType,
        FileStatus status,
        UserSummaryResponse actor,
        MyPermission myPermission,
        PermissionSource permissionSource,
        Instant createdAt,
        Instant updatedAt
) {
}
