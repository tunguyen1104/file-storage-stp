package com.filestorage.infrastructure.config;

import io.minio.MinioAsyncClient;
import io.minio.MinioClient;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties({MinioProperties.class, StorageProperties.class})
public class StorageConfig {

	@Bean
	public MinioClient minioClient(MinioProperties minioProperties) {
		return MinioClient.builder()
				.endpoint(minioProperties.getEndpoint())
				.credentials(minioProperties.getAccessKey(), minioProperties.getSecretKey())
				.build();
	}

	@Bean
	public MinioClient minioPresignedClient(MinioProperties minioProperties) {
		return MinioClient.builder()
				.endpoint(minioProperties.getExternalUrl())
				.credentials(minioProperties.getAccessKey(), minioProperties.getSecretKey())
				.region("us-east-1")
				.build();
	}

	/**
	 * Async MinIO client used exclusively for multipart upload operations
	 * ({@code createMultipartUploadAsync}, {@code completeMultipartUploadAsync},
	 * {@code abortMultipartUploadAsync}) which are defined on {@code S3Base} and
	 * are accessible only through {@link MinioAsyncClient}.
	 */
	@Bean
	public MinioAsyncClient minioAsyncClient(MinioProperties minioProperties) {
		return MinioAsyncClient.builder()
				.endpoint(minioProperties.getEndpoint())
				.credentials(minioProperties.getAccessKey(), minioProperties.getSecretKey())
				.build();
	}

}


