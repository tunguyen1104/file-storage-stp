package com.filestorage.adapter.in.rest.dto.response;

import java.time.Instant;
import java.util.UUID;

public record TenantResponse(
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

