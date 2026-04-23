package com.filestorage.adapter.out.storage;

import com.filestorage.domain.port.out.ObjectStoragePort;
import com.filestorage.infrastructure.config.MinioProperties;
import com.filestorage.infrastructure.config.StorageProperties;
import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.RemoveObjectArgs;
import io.minio.errors.MinioException;
import io.minio.http.Method;
import lombok.RequiredArgsConstructor;
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
import java.util.Map;

@Component
@RequiredArgsConstructor
public class MinioStorageAdapter implements ObjectStoragePort {

    private final MinioClient minioClient;
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
            return minioClient.getPresignedObjectUrl(
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
            return minioClient.getPresignedObjectUrl(
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



