package com.filestorage.adapter.in.rest.dto.response.share;

import com.filestorage.domain.model.enums.PermissionFile;
import com.filestorage.domain.model.enums.ShareRecipientType;
import com.filestorage.domain.model.enums.ShareStatus;

import java.time.Instant;
import java.util.UUID;

public record ShareResponse(
        UUID id,
        UUID tenantId,
        String tenantName,
        String resourceType,
        UUID fileId,
        UUID folderId,
        PermissionFile permission,
        ShareStatus status,
        ShareRecipientType recipientType,
        ShareUserResponse sharedWithUser,
        ShareTenantResponse sharedWithTenant,
        SharedByResponse sharedBy,
        Instant expiresAt,
        Instant createdAt,
        Instant revokedAt
) {
}
