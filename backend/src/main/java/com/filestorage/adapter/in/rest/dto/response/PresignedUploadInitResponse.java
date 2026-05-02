package com.filestorage.adapter.in.rest.dto.response;

import java.time.Instant;
import java.util.UUID;

public record PresignedUploadInitResponse(
        UUID fileId,
        String uploadUrl,
        Instant expiresAt
) {
}
