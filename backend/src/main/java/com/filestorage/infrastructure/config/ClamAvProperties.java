package com.filestorage.infrastructure.config;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for the ClamAV REST API sidecar.
 * <p>
 * Bound from {@code app.virus-scan.clamav.*} in application YAML.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.virus-scan.clamav")
public class ClamAvProperties {

    /** Hostname or IP of the ClamAV REST API service. */
    @NotBlank
    private String host = "localhost";

    /** Port of the ClamAV REST API service. */
    @Min(1)
    private int port = 3001;

    /** HTTP request timeout in milliseconds. */
    @Min(1000)
    private int timeoutMs = 30_000;

    @Min(1)
    private long maxScanFileSizeBytes = 209_715_200L;
}
