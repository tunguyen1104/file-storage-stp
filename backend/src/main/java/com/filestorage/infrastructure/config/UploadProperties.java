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

    @Min(1)
    private long maxFileSizeBytes = 4L * 1024 * 1024 * 1024; // 4 GiB

    /** Files remaining in PENDING status for longer than this threshold (hours) are cleaned up. */
    @Min(1)
    private int pendingCleanupThresholdHours = 1;

    /** Multipart-upload sub-properties. */
    private Multipart multipart = new Multipart();

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

    @Getter
    @Setter
    public static class VirusScan {

        /** Files at or above this size (MB) skip ClamAV scanning entirely. */
        @Min(1)
        private int bypassScanSizeMb = 50;

        /** MIME types that must be scanned when the file is below {@code bypassScanSizeMb}. */
        private List<String> scanMimeTypes = List.of(
                // Documents
                "application/pdf",
                "application/msword",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                "application/vnd.ms-excel",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "application/vnd.ms-powerpoint",
                "application/vnd.openxmlformats-officedocument.presentationml.presentation",
                "application/rtf",
                "text/rtf",
                "text/plain",
                "text/csv",
                "text/html",
                "application/xml",
                "text/xml",
                // Archives / compressed
                "application/zip",
                "application/x-zip-compressed",
                "application/x-rar-compressed",
                "application/x-7z-compressed",
                "application/x-tar",
                "application/gzip",
                "application/x-bzip2",
                // Executables and installers
                "application/x-msdownload",
                "application/x-executable",
                "application/x-msi",
                "application/x-deb",
                "application/x-rpm",
                "application/java-archive",
                "application/octet-stream",
                // Scripts
                "application/javascript",
                "text/javascript",
                "application/x-sh",
                "text/x-python",
                "application/x-python-code",
                // SVG (can embed scripts)
                "image/svg+xml"
        );
    }
}
