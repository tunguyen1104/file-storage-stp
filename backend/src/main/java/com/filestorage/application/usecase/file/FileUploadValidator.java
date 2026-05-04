package com.filestorage.application.usecase.file;

import com.filestorage.infrastructure.config.ClamAvProperties;
import com.filestorage.infrastructure.config.UploadProperties;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

/**
 * Application-layer guard for upload requests.
 * <p>
 * Two responsibilities:
 * <ol>
 *   <li><b>MIME whitelist</b> — rejects file types not in {@code app.upload.allowed-mime-types}
 *       at session-init time (fast path, client-declared type).</li>
 *   <li><b>Tika re-verification</b> — after the file is stored in MinIO, re-detects the real
 *       MIME type from the first bytes of the stream and re-applies the whitelist check to
 *       prevent MIME-spoofing attacks (e.g. renaming {@code evil.exe} to {@code report.pdf}).</li>
 *   <li><b>Scan bypass</b> — determines whether a file is large enough and of a trusted media
 *       type to skip ClamAV entirely.</li>
 * </ol>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class FileUploadValidator {

    private final UploadProperties uploadProperties;
    private final ClamAvProperties clamAvProperties;
    private final Tika tika = new Tika();

    /**
     * Validates that {@code mimeType} is in the configured MIME whitelist.
     *
     * @param mimeType client-declared or Tika-detected MIME type
     * @throws AppException with {@link ErrorCode#UNSUPPORTED_FILE_TYPE} if the type is not allowed
     */
    public void validateMimeType(String mimeType) {
        String normalised = normalise(mimeType);
        if (!uploadProperties.getAllowedMimeTypes().contains(normalised)) {
            log.warn("Rejected upload: MIME type '{}' is not in the allowed list", mimeType);
            throw new AppException(ErrorCode.UNSUPPORTED_FILE_TYPE);
        }
    }

    /**
     * Uses Apache Tika to detect the real MIME type from the first bytes of {@code inputStream},
     * validates it against the whitelist, and returns the detected type.
     * <p>
     * Use this variant when the detected MIME type is needed downstream (e.g. to make
     * accurate scan-bypass decisions based on the real type, not the client-declared one).
     *
     * @param inputStream      stream positioned at the start of the file content (not closed by this method)
     * @param declaredMimeType the MIME type declared at upload init time (used as Tika fallback)
     * @return the Tika-detected (and whitelist-validated) MIME type
     * @throws AppException with {@link ErrorCode#UNSUPPORTED_FILE_TYPE} if the detected type is not allowed
     */
    public String detectAndValidateMime(InputStream inputStream, String declaredMimeType) {
        String detected = detectMime(inputStream, declaredMimeType);
        log.debug("Tika detected MIME type: '{}' (declared: '{}')", detected, declaredMimeType);
        validateMimeType(detected);
        return detected;
    }

    /**
     * Returns {@code true} when this file should skip ClamAV virus scanning.
     * <p>
     * Bypass applies when <em>any</em> of the following conditions hold:
     * <ul>
     *   <li><b>Too large for ClamAV</b> — file size exceeds {@code app.virus-scan.clamav.max-scan-file-size-bytes}.
     *       Sending a larger file would cause HTTP 413 from the REST API sidecar.</li>
     *   <li><b>Trusted large media</b> — file size exceeds the bypass-size threshold
     *       ({@code app.upload.virus-scan.bypass-scan-size-mb}) AND the MIME type is in
     *       {@code app.upload.virus-scan.bypass-mime-types}.</li>
     * </ul>
     *
     * @param mimeType  the file's MIME type (Tika-detected, not client-declared)
     * @param sizeBytes the file's size in bytes
     * @return {@code true} if the file qualifies for scan bypass; {@code false} otherwise
     */
    public boolean shouldBypassScan(String mimeType, long sizeBytes) {
        // Guard: file exceeds ClamAV REST API size limit → bypass to prevent HTTP 413
        if (sizeBytes > clamAvProperties.getMaxScanFileSizeBytes()) {
            log.warn("Scan bypass (file too large for ClamAV): size={} bytes > maxScanFileSizeBytes={} bytes — activating directly",
                    sizeBytes, clamAvProperties.getMaxScanFileSizeBytes());
            return true;
        }

        // Bypass: trusted large-media type above configured threshold
        long bypassThresholdBytes = uploadProperties.getVirusScan().getBypassScanSizeMb() * 1024L * 1024L;
        String normalisedMime = normalise(mimeType);
        boolean exceedsThreshold = sizeBytes > bypassThresholdBytes;
        boolean isTrustedMediaType = uploadProperties.getVirusScan().getBypassMimeTypes().contains(normalisedMime);

        if (exceedsThreshold && isTrustedMediaType) {
            log.debug("Scan bypass (trusted media): mime='{}' size={}MB > threshold={}MB",
                    normalisedMime, sizeBytes / (1024L * 1024L),
                    uploadProperties.getVirusScan().getBypassScanSizeMb());
            return true;
        }

        return false;
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

    private String detectMime(InputStream inputStream, String fallback) {
        try {
            String detected = tika.detect(inputStream);
            return (detected != null && !detected.isBlank()) ? detected : normalise(fallback);
        } catch (IOException e) {
            log.warn("Tika MIME detection failed; falling back to declared type '{}': {}", fallback, e.getMessage());
            return normalise(fallback);
        }
    }

    /** Strips parameters (e.g. {@code ; charset=utf-8}) and lower-cases the MIME string. */
    private static String normalise(String mimeType) {
        if (mimeType == null) {
            return "";
        }
        int semicolon = mimeType.indexOf(';');
        String base = semicolon >= 0 ? mimeType.substring(0, semicolon) : mimeType;
        return base.trim().toLowerCase();
    }
}
