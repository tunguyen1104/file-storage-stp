package com.filestorage.adapter.in.rest.dto.response.share;

import com.filestorage.domain.model.enums.PermissionFile;
import com.filestorage.domain.model.enums.ShareRecipientType;
import com.filestorage.domain.model.enums.ShareStatus;

import java.time.Instant;
import java.util.UUID;

public record SharedWithMeResponse(
        UUID shareId,
        String resourceType,
        PermissionFile permission,
        ShareStatus status,
        ShareRecipientType recipientType,
        Instant expiresAt,
        SharedByResponse sharedBy,
        ShareResourceResponse resource
) {
}
