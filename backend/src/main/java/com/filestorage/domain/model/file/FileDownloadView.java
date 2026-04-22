package com.filestorage.domain.model.file;

import java.time.Instant;

public record FileDownloadView(
        String downloadUrl,
        Instant expiresAt,
        String originalName,
        String mimeType,
        long sizeBytes
) {
}

