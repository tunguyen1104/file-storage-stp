package com.filestorage.domain.model.file;

import java.time.Instant;

public record FilePresignedGetView(
        String getUrl,
        Instant expiresAt,
        String originalName,
        String mimeType,
        long sizeBytes
) {
}

