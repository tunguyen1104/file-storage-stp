package com.filestorage.domain.model.file;

/**
 * Domain view returned after a single part completion is acknowledged.
 *
 * @param uploadId          MinIO upload ID of the multipart session
 * @param partNumber        1-based index of the acknowledged part
 * @param uploadedPartsCount number of parts that have been confirmed so far
 * @param totalParts        total number of parts declared at session init
 */
public record PartCompletionView(
        String uploadId,
        int partNumber,
        int uploadedPartsCount,
        int totalParts
) {}
