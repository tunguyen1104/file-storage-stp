package com.filestorage.domain.port.out;

import com.filestorage.domain.model.enums.MultipartUploadStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Lightweight DTO cached in Redis for fast multipart-session lookups.
 * <p>
 * This is the canonical representation stored under the key
 * {@code multipart:session:{uploadId}} with a 24 h TTL.
 */
public record MultipartSessionCacheDto(
        String uploadId,
        UUID fileId,
        UUID initiatedByUserId,
        String objectKey,
        int totalParts,
        long totalSizeBytes,
        MultipartUploadStatus status,
        Instant expiresAt,
        List<PartInfo> uploadedParts
) {
    public record PartInfo(int partNumber, String etag) {}
}
