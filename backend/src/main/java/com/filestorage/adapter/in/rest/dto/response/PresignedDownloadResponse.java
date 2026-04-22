package com.filestorage.adapter.in.rest.dto.response;

import java.time.Instant;

public record PresignedDownloadResponse(
        String downloadUrl,
        Instant expiresAt,
        String originalName,
        String mimeType,
        long sizeBytes
) {
}

