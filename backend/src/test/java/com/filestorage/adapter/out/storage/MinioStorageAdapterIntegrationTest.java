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

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;

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

        adapter = new MinioStorageAdapter(minioClient, minioClient, minioProperties, storageProperties);
    }

    @Test
    void generatePresignedDownloadUrlUsesDefaultTtlWhenMissing() {
        String storageKey = adapter.buildStorageKey("tenant-it", "user-it", "file-it", "txt");
        adapter.upload(storageKey, "hello-minio".getBytes(StandardCharsets.UTF_8), "text/plain");

        String url = adapter.generatePresignedDownloadUrl(storageKey, "bao-cao.pdf", null);

        assertTrue(url.contains("X-Amz-Expires=900"));
        String disposition = readContentDisposition(url);
        assertTrue(disposition.startsWith("attachment;"));
        assertTrue(disposition.contains("filename*=UTF-8''bao-cao.pdf"));
    }

    @Test
    void generatePresignedGetUrlUsesInlineDisposition() {
        String storageKey = adapter.buildStorageKey("tenant-it", "user-it", "file-inline", "png");
        adapter.upload(storageKey, "img".getBytes(StandardCharsets.UTF_8), "image/png");

        String url = adapter.generatePresignedGetUrl(storageKey, "preview.png", null);

        String disposition = readContentDisposition(url);
        assertTrue(disposition.startsWith("inline;"));
        assertTrue(disposition.contains("filename*=UTF-8''preview.png"));
    }

    private static String readContentDisposition(String url) {
        String query = url.substring(url.indexOf('?') + 1);
        return Arrays.stream(query.split("&"))
                .map(pair -> pair.split("=", 2))
                .filter(parts -> parts.length == 2 && parts[0].equals("response-content-disposition"))
                .map(parts -> URLDecoder.decode(parts[1], StandardCharsets.UTF_8))
                .findFirst()
                .orElseThrow();
    }
}
