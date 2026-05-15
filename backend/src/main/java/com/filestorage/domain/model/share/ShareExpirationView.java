package com.filestorage.domain.model.share;

import com.filestorage.domain.model.enums.ShareStatus;

import java.time.Instant;
import java.util.UUID;

public record ShareExpirationView(
        UUID id,
        ShareStatus status,
        Instant expiresAt,
        Instant revokedAt,
        Instant updatedAt
) {
}
