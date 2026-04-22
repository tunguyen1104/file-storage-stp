package com.filestorage.infrastructure.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.storage")
public class StorageProperties {

    @Min(1)
    private int defaultDownloadTtlMinutes = 15;

    @Min(1)
    private long singleUploadMaxMb = 100;

    @Min(1)
    private long multipartThresholdMb = 100;

    @NotBlank
    private String keyPattern = "tenantId/userId/fileId.ext";
}

