package com.filestorage.adapter.in.rest.dto.response;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FolderResponse(
        UUID id,
        String name,
        UUID parentId,
        long sizeBytes,
        UUID tenantId,
        UUID ownerId,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt,
        List<BreadcrumbResponse> breadcrumb
) {
}

