package com.filestorage.domain.model.share;

import com.filestorage.domain.model.enums.PermissionFile;
import com.filestorage.domain.model.enums.ShareRecipientType;
import com.filestorage.domain.model.enums.ShareStatus;

import java.time.Instant;
import java.util.UUID;

public record ShareWithResourceView(
        UUID id,
        String resourceType,
        UUID fileId,
        UUID folderId,
        PermissionFile permission,
        ShareStatus status,
        ShareRecipientType recipientType,
        ShareResourceView resource,
        ShareUserView sharedWithUser,
        ShareTenantView sharedWithTenant,
        Instant expiresAt,
        Instant createdAt,
        Instant revokedAt
) {
}
