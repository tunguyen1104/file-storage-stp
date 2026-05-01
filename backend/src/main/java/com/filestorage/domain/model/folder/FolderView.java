package com.filestorage.domain.model.folder;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public record FolderView(
        UUID id,
        String name,
        UUID parentId,
        long sizeBytes,
        UUID tenantId,
        UUID ownerId,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt,
        List<BreadcrumbView> breadcrumb
) {
}

