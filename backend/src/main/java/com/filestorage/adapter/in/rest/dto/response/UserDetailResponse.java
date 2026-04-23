package com.filestorage.adapter.in.rest.dto.response;

import java.time.Instant;
import java.util.UUID;

public record UserDetailResponse(
        UUID id,
        String email,
        String fullName,
        String role,
        UUID tenantId,
        String tenantName,
        boolean isActive,
        long storageQuotaBytes,
        long storageUsedBytes,
        String createdBy,
        Instant lastLoginAt,
        Instant passwordChangedAt,
        Instant createdAt,
        Instant updatedAt
) {
}

