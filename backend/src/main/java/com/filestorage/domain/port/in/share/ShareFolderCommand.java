package com.filestorage.domain.port.in.share;

import com.filestorage.domain.model.enums.PermissionFile;
import com.filestorage.domain.model.enums.RoleCode;

import java.time.Instant;
import java.util.UUID;

public record ShareFolderCommand(
        UUID actorId,
        UUID actorTenantId,
        RoleCode actorRole,
        UUID folderId,
        String sharedWithUserEmail,
        PermissionFile permission,
        Instant expiresAt
) {
}
