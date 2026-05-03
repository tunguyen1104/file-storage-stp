package com.filestorage.infrastructure.config;

import jakarta.validation.constraints.Min;
import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.util.List;

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

    /**
     * MIME types permitted for upload. Requests declaring a type outside this list are rejected
     * immediately at session-init time, before any bytes reach object storage.
     */
    private List<String> allowedMimeTypes = List.of(
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/webp",
            "image/svg+xml",
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "text/plain",
            "text/csv",
            "video/mp4",
            "video/quicktime",
            "video/x-msvideo",
            "audio/mpeg",
            "audio/wav",
            "audio/flac",
            "audio/ogg"
    );

    /** Virus-scan bypass sub-properties. */
    private VirusScan virusScan = new VirusScan();

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

    /**
     * Controls which files are allowed to skip the ClamAV scan.
     * <p>
     * A file bypasses scanning only when <em>both</em> conditions hold:
     * <ol>
     *   <li>Its declared size exceeds {@code bypassScanSizeMb} megabytes.</li>
     *   <li>Its MIME type is in {@code bypassMimeTypes}.</li>
     * </ol>
     * Files below the size threshold are always scanned, even for bypass-eligible MIME types.
     */
    @Getter
    @Setter
    public static class VirusScan {

        /** File size threshold in MB above which bypass-eligible MIME types skip ClamAV. */
        @Min(1)
        private int bypassScanSizeMb = 50;

        /** MIME types eligible for scan bypass when the file exceeds {@code bypassScanSizeMb}. */
        private List<String> bypassMimeTypes = List.of(
                "video/mp4",
                "video/quicktime",
                "video/x-msvideo",
                "audio/mpeg",
                "audio/wav",
                "audio/flac",
                "audio/ogg"
        );
    }
}
