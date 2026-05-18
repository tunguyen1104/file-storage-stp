package com.filestorage.domain.port.in.share;

import com.filestorage.domain.model.enums.RoleCode;

import java.time.Instant;
import java.util.UUID;

public record ShareFileCommand(
        UUID actorId,
        UUID actorTenantId,
        RoleCode actorRole,
        UUID fileId,
        String sharedWithUserEmail,
        Instant expiresAt
) {
}
