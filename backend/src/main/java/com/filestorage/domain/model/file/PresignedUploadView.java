package com.filestorage.domain.model.file;

import java.time.Instant;
import java.util.UUID;

public record PresignedUploadView(
        UUID fileId,
        String uploadUrl,
        Instant expiresAt
) {
}
