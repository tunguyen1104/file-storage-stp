package com.filestorage.domain.model.file;

import com.filestorage.domain.model.enums.MultipartUploadStatus;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Domain view of a multipart upload session — used by the GET session endpoint
 * to support resume-upload scenarios.
 *
 * @param uploadId     MinIO upload ID
 * @param fileId       UUID of the pre-created file row
 * @param status       current session status
 * @param totalParts   number of parts declared at init
 * @param uploadedParts list of parts already confirmed by the client
 * @param expiresAt    when the session expires
 */
public record MultipartSessionView(
        String uploadId,
        UUID fileId,
        MultipartUploadStatus status,
        int totalParts,
        List<PartInfo> uploadedParts,
        Instant expiresAt
) {
    /**
     * Mirrors {@link com.filestorage.adapter.out.persistence.entity.MultipartUploadSession.PartInfo}
     * but lives in the domain layer so adapters are not imported from the domain.
     */
    public record PartInfo(int partNumber, String etag) {}
}
