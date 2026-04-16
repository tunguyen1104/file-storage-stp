package com.filestorage.domain.model.user;

import java.time.Instant;
import java.util.UUID;

public record UserView(
        UUID id,
        String email,
        String fullName,
        UserRoleView role,
        UUID tenantId,
        boolean isActive,
        long storageQuotaBytes,
        long storageUsedBytes,
        UUID createdBy,
        Instant lastLoginAt,
        Instant passwordChangedAt,
        Instant createdAt,
        Instant updatedAt
) {
}
