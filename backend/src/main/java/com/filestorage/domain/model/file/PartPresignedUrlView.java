package com.filestorage.domain.model.file;

import java.time.Instant;

/**
 * Domain view returned when the client requests a presigned PUT URL for a single part.
 *
 * @param uploadId   MinIO upload ID of the multipart session
 * @param partNumber 1-based index of the part
 * @param partUrl    presigned PUT URL the client uses to upload the chunk directly to MinIO
 * @param expiresAt  when the presigned URL expires
 */
public record PartPresignedUrlView(
        String uploadId,
        int partNumber,
        String partUrl,
        Instant expiresAt
) {}
