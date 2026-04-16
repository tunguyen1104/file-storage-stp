package com.filestorage.adapter.in.rest.dto.response;

import java.time.Instant;
import java.util.UUID;

public record UserDetailResponse(
        UUID id,
        String email,
        String fullName,
        RoleSummaryResponse role,
        UUID tenantId,
        boolean isActive,
        long storageQuotaBytes,
        long storageUsedBytes,
        Instant lastLoginAt,
        Instant passwordChangedAt,
        Instant createdAt,
        Instant updatedAt
) {
}

