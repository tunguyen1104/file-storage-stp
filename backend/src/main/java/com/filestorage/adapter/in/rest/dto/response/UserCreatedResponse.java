package com.filestorage.adapter.in.rest.dto.response;

import java.time.Instant;
import java.util.UUID;

public record UserCreatedResponse(
        UUID id,
        String email,
        String fullName,
        String role,
        UUID tenantId,
        boolean isActive,
        long storageQuotaBytes,
        long storageUsedBytes,
        UUID createdBy,
        Instant createdAt
) {
}

