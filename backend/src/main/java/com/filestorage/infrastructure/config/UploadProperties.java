package com.filestorage.infrastructure.config;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Configuration properties for the upload workflows (presigned single-file and chunked multipart).
 * <p>
 * Bound from {@code app.upload.*} in application YAML.
 */
@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.upload")
public class UploadProperties {

    /** TTL (minutes) for presigned PUT URLs issued to clients (single-file upload). */
    @Min(1)
    private int presignedPutTtlMinutes = 15;

    /** Files remaining in PENDING status for longer than this threshold (hours) are cleaned up. */
    @Min(1)
    private int pendingCleanupThresholdHours = 1;

    /** Multipart-upload sub-properties. */
    private Multipart multipart = new Multipart();

    @Getter
    @Setter
    public static class Multipart {

        /** TTL (minutes) for presigned part PUT URLs. */
        @Min(1)
        private int partUrlTtlMinutes = 30;

        /** How long (hours) a multipart session stays alive before automatic cleanup. */
        @Min(1)
        private int sessionTtlHours = 24;

        /** How often (minutes) the expired-session cleanup job runs. */
        @Min(1)
        private int cleanupIntervalMinutes = 30;
    }
}
