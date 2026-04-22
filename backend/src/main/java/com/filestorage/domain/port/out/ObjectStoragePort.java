package com.filestorage.domain.port.out;

import java.time.Duration;

public interface ObjectStoragePort {

    String buildStorageKey(String tenantId, String userId, String fileId, String extension);

    void upload(String storageKey, byte[] content, String contentType);

    byte[] download(String storageKey);

    void delete(String storageKey);

    String generatePresignedDownloadUrl(String storageKey, String originalName, Duration ttl);

    String generatePresignedGetUrl(String storageKey, String originalName, Duration ttl);
}

