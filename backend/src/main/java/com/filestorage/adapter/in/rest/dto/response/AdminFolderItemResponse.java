package com.filestorage.adapter.in.rest.dto.response;

import com.filestorage.domain.model.enums.FileStatus;

import java.time.Instant;
import java.util.UUID;

public record AdminFolderItemResponse(
        UUID id,
        String type,
        String name,
        UUID tenantId,
        String tenantName,
        UUID parentId,
        String parentName,
        long sizeBytes,
        String mimeType,
        FileStatus status,
        UserSummaryResponse actor,
        Instant createdAt,
        Instant updatedAt
) {
}
