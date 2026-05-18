package com.filestorage.domain.model.share;

import com.filestorage.domain.model.enums.PermissionFile;
import com.filestorage.domain.model.enums.ShareRecipientType;
import com.filestorage.domain.model.enums.ShareStatus;

import java.time.Instant;
import java.util.UUID;

public record SharedWithMeView(
        UUID shareId,
        String resourceType,
        PermissionFile permission,
        ShareStatus status,
        ShareRecipientType recipientType,
        Instant expiresAt,
        SharedByView sharedBy,
        ShareResourceView resource
) {
}
