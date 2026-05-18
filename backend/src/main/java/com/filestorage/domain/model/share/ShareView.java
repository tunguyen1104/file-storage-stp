package com.filestorage.domain.model.share;

import com.filestorage.domain.model.enums.PermissionFile;
import com.filestorage.domain.model.enums.ShareRecipientType;
import com.filestorage.domain.model.enums.ShareStatus;

import java.time.Instant;
import java.util.UUID;

public record ShareView(
        UUID id,
        UUID tenantId,
        String resourceType,
        UUID fileId,
        UUID folderId,
        PermissionFile permission,
        ShareStatus status,
        ShareRecipientType recipientType,
        ShareUserView sharedWithUser,
        ShareTenantView sharedWithTenant,
        SharedByView sharedBy,
        Instant expiresAt,
        Instant createdAt,
        Instant revokedAt
) {
}
