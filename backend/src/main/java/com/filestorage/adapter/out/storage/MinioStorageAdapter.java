package com.filestorage.adapter.out.storage;

import com.filestorage.domain.port.out.ObjectStoragePort;
import com.filestorage.infrastructure.config.MinioProperties;
import com.filestorage.infrastructure.config.StorageProperties;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioAsyncClient;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.StatObjectArgs;
import io.minio.StatObjectResponse;
import io.minio.errors.ErrorResponseException;
import io.minio.errors.InsufficientDataException;
import io.minio.errors.InternalException;
import io.minio.errors.MinioException;
import io.minio.errors.XmlParserException;
import io.minio.http.Method;
import io.minio.messages.Part;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutionException;

@Component
@RequiredArgsConstructor
@Slf4j
public class MinioStorageAdapter implements ObjectStoragePort {

    private final MinioClient minioClient;
    private final MinioClient minioPresignedClient;
    private final MinioAsyncClient minioAsyncClient;
    private final MinioProperties minioProperties;
    private final StorageProperties storageProperties;

    @Override
    public String buildStorageKey(String tenantId, String userId, String fileId, String extension) {
        String tenant = requireNonBlank(tenantId, "tenantId");
        String user = requireNonBlank(userId, "userId");
        String file = requireNonBlank(fileId, "fileId");

        String normalizedExt = extension == null ? "" : extension.trim();
        if (normalizedExt.startsWith(".")) {
            normalizedExt = normalizedExt.substring(1);
        }

        if (normalizedExt.isEmpty()) {
            return tenant + "/" + user + "/" + file;
        }

        return tenant + "/" + user + "/" + file + "." + normalizedExt;
    }

    @Override
    public void upload(String storageKey, byte[] content, String contentType) {
        requireNonBlank(storageKey, "storageKey");
        if (content == null || content.length == 0) {
            throw new IllegalArgumentException("content must not be empty");
        }

        String effectiveContentType = (contentType == null || contentType.isBlank())
                ? "application/octet-stream"
                : contentType;

        try {
            minioClient.putObject(
                    PutObjectArgs.builder()
                            .bucket(minioProperties.getBucket())
                            .object(storageKey)
                            .stream(new ByteArrayInputStream(content), content.length, -1)
                            .contentType(effectiveContentType)
                            .build()
            );
        } catch (MinioException | IOException | NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to upload object to MinIO", e);
        }
    }

    @Override
    public byte[] download(String storageKey) {
        requireNonBlank(storageKey, "storageKey");
        try (InputStream in = minioClient.getObject(
                GetObjectArgs.builder()
                        .bucket(minioProperties.getBucket())
                        .object(storageKey)
                        .build()
        )) {
            return in.readAllBytes();
        } catch (MinioException | IOException | NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to download object from MinIO", e);
        }
    }

    @Override
    public void delete(String storageKey) {
        requireNonBlank(storageKey, "storageKey");
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(minioProperties.getBucket())
                            .object(storageKey)
                            .build()
            );
        } catch (MinioException | IOException | NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to delete object from MinIO", e);
        }
    }

    @Override
    public String generatePresignedDownloadUrl(String storageKey, String originalName, Duration ttl) {
        Duration effectiveTtl = ttl == null ? Duration.ofMinutes(storageProperties.getDefaultDownloadTtlMinutes()) : ttl;
        requireNonBlank(storageKey, "storageKey");
        if (effectiveTtl.isNegative() || effectiveTtl.isZero()) {
            throw new IllegalArgumentException("ttl must be greater than zero");
        }

        try {
            return minioPresignedClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(minioProperties.getBucket())
                            .object(storageKey)
                            .extraQueryParams(contentDispositionParams(buildAttachmentContentDisposition(originalName)))
                            .expiry(Math.toIntExact(effectiveTtl.getSeconds()))
                            .build()
            );

        } catch (MinioException | IOException | NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to generate MinIO presigned URL", e);
        }
    }

    @Override
    public String generatePresignedGetUrl(String storageKey, String originalName, Duration ttl) {
        Duration effectiveTtl = ttl == null ? Duration.ofMinutes(storageProperties.getDefaultDownloadTtlMinutes()) : ttl;
        requireNonBlank(storageKey, "storageKey");
        if (effectiveTtl.isNegative() || effectiveTtl.isZero()) {
            throw new IllegalArgumentException("ttl must be greater than zero");
        }

        try {
            return minioPresignedClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.GET)
                            .bucket(minioProperties.getBucket())
                            .object(storageKey)
                            .extraQueryParams(contentDispositionParams(buildInlineContentDisposition(originalName)))
                            .expiry(Math.toIntExact(effectiveTtl.getSeconds()))
                            .build()
            );
        } catch (MinioException | IOException | NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to generate MinIO presigned URL", e);
        }
    }

    @Override
    public void deleteObjectIfExists(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return;
        }
        try {
            minioClient.removeObject(
                    RemoveObjectArgs.builder()
                            .bucket(minioProperties.getBucket())
                            .object(storageKey)
                            .build()
            );
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                log.debug("deleteObjectIfExists: object not found, skipping — key={}", storageKey);
                return;
            }
            throw new IllegalStateException("Failed to delete object from MinIO: " + storageKey, e);
        } catch (MinioException | IOException | NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to delete object from MinIO: " + storageKey, e);
        }
    }

    @Override
    public String generatePresignedPutUrl(String storageKey, String contentType, int ttlMinutes) {
        requireNonBlank(storageKey, "storageKey");
        if (ttlMinutes <= 0) {
            throw new IllegalArgumentException("ttlMinutes must be greater than zero");
        }
        try {
            return minioPresignedClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(minioProperties.getBucket())
                            .object(storageKey)
                            .expiry(ttlMinutes * 60)
                            .build()
            );
        } catch (MinioException | IOException | NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to generate presigned PUT URL for MinIO", e);
        }
    }

    @Override
    public boolean objectExists(String storageKey) {
        requireNonBlank(storageKey, "storageKey");
        try {
            minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(minioProperties.getBucket())
                            .object(storageKey)
                            .build()
            );
            return true;
        } catch (ErrorResponseException e) {
            if ("NoSuchKey".equals(e.errorResponse().code())) {
                return false;
            }
            throw new IllegalStateException("Failed to stat object in MinIO: " + storageKey, e);
        } catch (MinioException | IOException | NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to stat object in MinIO: " + storageKey, e);
        }
    }

    @Override
    public ObjectMetadata getObjectMetadata(String storageKey) {
        requireNonBlank(storageKey, "storageKey");
        try {
            StatObjectResponse stat = minioClient.statObject(
                    StatObjectArgs.builder()
                            .bucket(minioProperties.getBucket())
                            .object(storageKey)
                            .build()
            );
            return new ObjectMetadata(stat.size(), stat.contentType());
        } catch (MinioException | IOException | NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to retrieve object metadata from MinIO: " + storageKey, e);
        }
    }

    // ── Multipart upload operations ───────────────────────────────────────────

    @Override
    public String initiateMultipartUpload(String storageKey, String contentType) {
        requireNonBlank(storageKey, "storageKey");
        String effectiveContentType = (contentType == null || contentType.isBlank())
                ? "application/octet-stream"
                : contentType;

        Multimap<String, String> headers = HashMultimap.create();
        headers.put("Content-Type", effectiveContentType);

        try {
            var response = minioAsyncClient.createMultipartUploadAsync(
                    minioProperties.getBucket(), null, storageKey, headers, null
            ).get();
            return response.result().uploadId();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while initiating multipart upload for key: " + storageKey, e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to initiate multipart upload for key: " + storageKey, e.getCause());
        } catch (InsufficientDataException | InternalException | XmlParserException
                 | IOException | NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to initiate multipart upload for key: " + storageKey, e);
        }
    }

    @Override
    public String generatePresignedPartUrl(String uploadId, String storageKey, int partNumber, int ttlMinutes) {
        requireNonBlank(storageKey, "storageKey");
        if (partNumber < 1) {
            throw new IllegalArgumentException("partNumber must be >= 1");
        }
        if (ttlMinutes <= 0) {
            throw new IllegalArgumentException("ttlMinutes must be > 0");
        }

        Map<String, String> extraQueryParams = new HashMap<>();
        extraQueryParams.put("uploadId", uploadId);
        extraQueryParams.put("partNumber", String.valueOf(partNumber));

        try {
            return minioPresignedClient.getPresignedObjectUrl(
                    GetPresignedObjectUrlArgs.builder()
                            .method(Method.PUT)
                            .bucket(minioProperties.getBucket())
                            .object(storageKey)
                            .expiry(ttlMinutes * 60)
                            .extraQueryParams(extraQueryParams)
                            .build()
            );
        } catch (MinioException | IOException | NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to generate presigned part URL for key: " + storageKey, e);
        }
    }

    @Override
    public void completeMultipartUpload(String uploadId, String storageKey, List<PartDescriptor> parts) {
        requireNonBlank(storageKey, "storageKey");
        if (parts == null || parts.isEmpty()) {
            throw new IllegalArgumentException("parts must not be empty");
        }

        Part[] minioParts = parts.stream()
                .map(p -> new Part(p.partNumber(), p.etag()))
                .toArray(Part[]::new);

        try {
            minioAsyncClient.completeMultipartUploadAsync(
                    minioProperties.getBucket(), null, storageKey, uploadId, minioParts, null, null
            ).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while completing multipart upload for key: " + storageKey, e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to complete multipart upload for key: " + storageKey, e.getCause());
        } catch (InsufficientDataException | InternalException | XmlParserException
                 | IOException | NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to complete multipart upload for key: " + storageKey, e);
        }
    }

    @Override
    public void abortMultipartUpload(String uploadId, String storageKey) {
        requireNonBlank(storageKey, "storageKey");

        try {
            minioAsyncClient.abortMultipartUploadAsync(
                    minioProperties.getBucket(), null, storageKey, uploadId, null, null
            ).get();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Interrupted while aborting multipart upload for key: " + storageKey, e);
        } catch (ExecutionException e) {
            throw new IllegalStateException("Failed to abort multipart upload for key: " + storageKey, e.getCause());
        } catch (InsufficientDataException | InternalException | XmlParserException
                 | IOException | NoSuchAlgorithmException | InvalidKeyException e) {
            throw new IllegalStateException("Failed to abort multipart upload for key: " + storageKey, e);
        }
    }

    private static Map<String, String> contentDispositionParams(String contentDisposition) {
        Map<String, String> params = new HashMap<>();
        params.put("response-content-disposition", contentDisposition);
        return params;
    }

    private static String buildAttachmentContentDisposition(String originalName) {
        return "attachment; " + buildFilenameParameters(originalName);
    }

    private static String buildInlineContentDisposition(String originalName) {
        return "inline; " + buildFilenameParameters(originalName);
    }

    private static String buildFilenameParameters(String originalName) {
        String effectiveName = (originalName == null || originalName.isBlank()) ? "download" : originalName.trim();
        String asciiFallback = toAsciiFilenameFallback(effectiveName);
        String encoded = URLEncoder.encode(effectiveName, StandardCharsets.UTF_8).replace("+", "%20");
        return "filename=\"" + asciiFallback + "\"; filename*=UTF-8''" + encoded;
    }

    private static String toAsciiFilenameFallback(String filename) {
        String cleaned = filename
                .replace("\\", "_")
                .replace("\"", "_")
                .replace(";", "_");
        String ascii = cleaned.replaceAll("[^\\x20-\\x7E]", "_");
        return ascii.isBlank() ? "download" : ascii;
    }

    private static String requireNonBlank(String value, String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }
}



