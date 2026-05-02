package com.filestorage.infrastructure.config;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for the presigned-upload workflow.
 * <p>
 * Bound from {@code app.upload.*} in application YAML.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.upload")
public class UploadProperties {

    /** TTL (minutes) for presigned PUT URLs issued to clients. */
    @Min(1)
    private int presignedPutTtlMinutes = 15;

    /** Files remaining in PENDING status for longer than this threshold (hours) are cleaned up. */
    @Min(1)
    private int pendingCleanupThresholdHours = 1;
}
