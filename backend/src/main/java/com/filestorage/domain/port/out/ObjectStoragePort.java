package com.filestorage.domain.port.out;

import java.time.Duration;
import java.util.List;

public interface ObjectStoragePort {

    String buildStorageKey(String tenantId, String userId, String fileId, String extension);

    void upload(String storageKey, byte[] content, String contentType);

    byte[] download(String storageKey);

    void delete(String storageKey);

    /**
     * Deletes the object if it exists; silently ignores the case where the object is not present.
     */
    void deleteObjectIfExists(String storageKey);

    String generatePresignedDownloadUrl(String storageKey, String originalName, Duration ttl);

    String generatePresignedGetUrl(String storageKey, String originalName, Duration ttl);

    /**
     * Generates a presigned PUT URL so the client can upload directly to object storage.
     *
     * @param storageKey  object path inside the bucket
     * @param contentType declared MIME type
     * @param ttlMinutes  URL validity window in minutes
     * @return presigned PUT URL as a string
     */
    String generatePresignedPutUrl(String storageKey, String contentType, int ttlMinutes);

    /**
     * Returns {@code true} if the object identified by {@code storageKey} exists in the bucket.
     */
    boolean objectExists(String storageKey);

    /**
     * Retrieves the actual metadata of an existing object from storage.
     * Used to verify that the file the client uploaded matches the declared metadata.
     *
     * @param storageKey object path inside the bucket
     * @return {@link ObjectMetadata} with actual size and content-type
     * @throws IllegalStateException if the object does not exist or the call fails
     */
    ObjectMetadata getObjectMetadata(String storageKey);

    /** Value object carrying the actual metadata of a stored object. */
    record ObjectMetadata(long sizeBytes, String contentType) {}

    // ── Multipart upload operations ───────────────────────────────────────────

    /**
     * Initiates a multipart upload on MinIO/S3 and returns the server-generated upload ID.
     *
     * @param storageKey  object path inside the bucket
     * @param contentType declared MIME type
     * @return MinIO-generated uploadId string
     */
    String initiateMultipartUpload(String storageKey, String contentType);

    /**
     * Generates a presigned PUT URL for a single part of a multipart upload.
     *
     * @param uploadId   MinIO upload ID returned by {@link #initiateMultipartUpload}
     * @param storageKey object path inside the bucket
     * @param partNumber 1-based part index
     * @param ttlMinutes URL validity window in minutes
     * @return presigned PUT URL as a string
     */
    String generatePresignedPartUrl(String uploadId, String storageKey, int partNumber, int ttlMinutes);

    /**
     * Instructs MinIO/S3 to assemble all uploaded parts into the final object.
     * No file bytes flow through the backend.
     *
     * @param uploadId   MinIO upload ID
     * @param storageKey object path inside the bucket
     * @param parts      ordered list of part descriptors (partNumber + ETag)
     */
    void completeMultipartUpload(String uploadId, String storageKey, List<PartDescriptor> parts);

    /**
     * Aborts a multipart upload and removes all intermediate parts from storage.
     *
     * @param uploadId   MinIO upload ID
     * @param storageKey object path inside the bucket
     */
    void abortMultipartUpload(String uploadId, String storageKey);

    /** Domain-safe descriptor of a completed multipart part. */
    record PartDescriptor(int partNumber, String etag) {}
}

