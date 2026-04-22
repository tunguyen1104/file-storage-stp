package com.filestorage.adapter.in.rest.dto.response;

import java.time.Instant;

public record PresignedGetUrlResponse(
        String getUrl,
        Instant expiresAt,
        String originalName,
        String mimeType,
        long sizeBytes
) {
}

