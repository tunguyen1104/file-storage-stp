package com.filestorage.adapter.out.storage;

import com.filestorage.infrastructure.config.MinioProperties;
import com.filestorage.infrastructure.config.StorageProperties;
import io.minio.BucketExistsArgs;
import io.minio.MakeBucketArgs;
import io.minio.MinioClient;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestInstance;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;
import org.testcontainers.containers.wait.strategy.Wait;

import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertTrue;

@Testcontainers
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class MinioStorageAdapterIntegrationTest {

    private static final String MINIO_USER = "minioadmin";
    private static final String MINIO_PASSWORD = "minioadmin123";
    private static final String BUCKET = "file-storage-it";

    @Container
    static final GenericContainer<?> minio = new GenericContainer<>(
            DockerImageName.parse("minio/minio:RELEASE.2024-06-13T22-53-53Z")
    )
            .withEnv("MINIO_ROOT_USER", MINIO_USER)
            .withEnv("MINIO_ROOT_PASSWORD", MINIO_PASSWORD)
            .withCommand("server", "/data", "--console-address", ":9001")
            .withExposedPorts(9000, 9001)
            .waitingFor(Wait.forHttp("/minio/health/live").forPort(9000));

    private MinioStorageAdapter adapter;

    @BeforeAll
    void setUp() throws Exception {
        String endpoint = "http://" + minio.getHost() + ":" + minio.getMappedPort(9000);
        MinioClient minioClient = MinioClient.builder()
                .endpoint(endpoint)
                .credentials(MINIO_USER, MINIO_PASSWORD)
                .build();

        if (!minioClient.bucketExists(BucketExistsArgs.builder().bucket(BUCKET).build())) {
            minioClient.makeBucket(MakeBucketArgs.builder().bucket(BUCKET).build());
        }

        MinioProperties minioProperties = new MinioProperties();
        minioProperties.setEndpoint(endpoint);
        minioProperties.setAccessKey(MINIO_USER);
        minioProperties.setSecretKey(MINIO_PASSWORD);
        minioProperties.setBucket(BUCKET);

        StorageProperties storageProperties = new StorageProperties();
        storageProperties.setDefaultDownloadTtlMinutes(15);

        adapter = new MinioStorageAdapter(minioClient, minioProperties, storageProperties);
    }

    @Test
    void generatePresignedDownloadUrlUsesDefaultTtlWhenMissing() {
        String storageKey = adapter.buildStorageKey("tenant-it", "user-it", "file-it", "txt");
        adapter.upload(storageKey, "hello-minio".getBytes(StandardCharsets.UTF_8), "text/plain");

        String url = adapter.generatePresignedDownloadUrl(storageKey, null);

        assertTrue(url.contains("X-Amz-Expires=900"));
    }
}

