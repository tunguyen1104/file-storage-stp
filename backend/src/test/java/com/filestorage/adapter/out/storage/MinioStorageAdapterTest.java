package com.filestorage.adapter.out.storage;

import com.filestorage.infrastructure.config.MinioProperties;
import com.filestorage.infrastructure.config.StorageProperties;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MinioStorageAdapterTest {

    private MinioStorageAdapter adapter;

    @BeforeEach
    void setUp() {
        MinioProperties minioProperties = new MinioProperties();
        minioProperties.setBucket("file-storage");
        minioProperties.setEndpoint("http://localhost:9000");
        minioProperties.setAccessKey("minioadmin");
        minioProperties.setSecretKey("minioadmin123");

        StorageProperties storageProperties = new StorageProperties();
        MinioClient minioClient = Mockito.mock(MinioClient.class);
        adapter = new MinioStorageAdapter(minioClient, minioClient, minioProperties, storageProperties);
    }

    @Test
    void buildStorageKeyWithExtension() {
        String key = adapter.buildStorageKey("tenant-uuid", "user-uuid", "file-uuid", "jpg");
        assertEquals("tenant-uuid/user-uuid/file-uuid.jpg", key);
    }

    @Test
    void buildStorageKeyWithDotExtension() {
        String key = adapter.buildStorageKey("tenant-uuid", "user-uuid", "file-uuid", ".pdf");
        assertEquals("tenant-uuid/user-uuid/file-uuid.pdf", key);
    }
}
