package com.filestorage.adapter.in.rest.dto.response;

import java.time.Instant;
import java.util.UUID;

public record UserListItemResponse(
        UUID id,
        String email,
        String fullName,
        RoleSummaryResponse role,
        boolean isActive,
        long storageQuotaBytes,
        long storageUsedBytes,
        Instant lastLoginAt,
        Instant createdAt
) {
}

