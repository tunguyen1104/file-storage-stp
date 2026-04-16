package com.filestorage.domain.model.tenant;

import java.time.Instant;
import java.util.UUID;

public record TenantView(
        UUID id,
        String name,
        String slug,
        long storageQuotaBytes,
        long storageUsedBytes,
        boolean isActive,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt
) {
}

