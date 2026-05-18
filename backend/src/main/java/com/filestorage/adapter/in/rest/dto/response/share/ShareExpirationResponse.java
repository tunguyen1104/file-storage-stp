package com.filestorage.adapter.in.rest.dto.response.share;

import com.filestorage.domain.model.enums.ShareStatus;

import java.time.Instant;
import java.util.UUID;

public record ShareExpirationResponse(
        UUID id,
        ShareStatus status,
        Instant expiresAt,
        Instant revokedAt,
        Instant updatedAt
) {
}
