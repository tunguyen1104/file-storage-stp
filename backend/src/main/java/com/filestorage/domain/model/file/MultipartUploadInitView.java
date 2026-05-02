package com.filestorage.domain.model.file;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain view returned after a multipart upload session is initialised.
 *
 * @param uploadId  MinIO-generated upload ID that identifies the multipart session
 * @param fileId    UUID of the pre-created {@code files} row (status = PENDING)
 * @param expiresAt when the session (and presigned part URLs) expire (now + 24 h)
 */
public record MultipartUploadInitView(
        String uploadId,
        UUID fileId,
        Instant expiresAt
) {}
