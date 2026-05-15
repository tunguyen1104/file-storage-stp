package com.filestorage.domain.port.in.share;

import com.filestorage.domain.model.enums.RoleCode;

import java.time.Instant;
import java.util.UUID;

public record ShareWithTenantCommand(
        UUID actorId,
        UUID actorTenantId,
        RoleCode actorRole,
        UUID resourceId,
        Instant expiresAt
) {
}
