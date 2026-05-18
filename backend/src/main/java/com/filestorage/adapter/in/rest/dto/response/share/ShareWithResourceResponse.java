package com.filestorage.adapter.in.rest.dto.response.share;

import com.filestorage.domain.model.enums.PermissionFile;
import com.filestorage.domain.model.enums.ShareRecipientType;
import com.filestorage.domain.model.enums.ShareStatus;

import java.time.Instant;
import java.util.UUID;

public record ShareWithResourceResponse(
        UUID id,
        String resourceType,
        UUID fileId,
        UUID folderId,
        PermissionFile permission,
        ShareStatus status,
        ShareRecipientType recipientType,
        ShareResourceResponse resource,
        ShareUserResponse sharedWithUser,
        ShareTenantResponse sharedWithTenant,
        Instant expiresAt,
        Instant createdAt,
        Instant revokedAt
) {
}
