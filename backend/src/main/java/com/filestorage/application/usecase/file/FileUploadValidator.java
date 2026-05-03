package com.filestorage.application.usecase.file;

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
     * then validates the detected type against the whitelist.
     * <p>
     * Call this after the file has been retrieved from object storage to prevent MIME-spoofing.
     * If Tika detection fails, the method falls back to {@code declaredMimeType} and validates
     * that instead — the earlier whitelist check at init time already covered it.
     *
     * @param inputStream    stream positioned at the start of the file content (not closed by this method)
     * @param declaredMimeType the MIME type that was declared at upload init time (fallback)
     * @throws AppException with {@link ErrorCode#UNSUPPORTED_FILE_TYPE} if the detected type is not allowed
     */
    public void rejectIfMimeSpoofed(InputStream inputStream, String declaredMimeType) {
        String detected = detectMime(inputStream, declaredMimeType);
        log.debug("Tika detected MIME type: '{}' (declared: '{}')", detected, declaredMimeType);
        validateMimeType(detected);
    }

    /**
     * Returns {@code true} when this file should skip ClamAV virus scanning.
     * <p>
     * Bypass applies only when <em>both</em> conditions hold:
     * <ul>
     *   <li>The file size exceeds the configured threshold ({@code app.upload.virus-scan.bypass-scan-size-mb} MB).</li>
     *   <li>The MIME type is in {@code app.upload.virus-scan.bypass-mime-types}.</li>
     * </ul>
     *
     * @param mimeType  the file's MIME type
     * @param sizeBytes the file's size in bytes
     * @return {@code true} if the file qualifies for scan bypass; {@code false} otherwise
     */
    public boolean shouldBypassScan(String mimeType, long sizeBytes) {
        long thresholdBytes = uploadProperties.getVirusScan().getBypassScanSizeMb() * 1024L * 1024L;
        return sizeBytes > thresholdBytes
                && uploadProperties.getVirusScan().getBypassMimeTypes().contains(normalise(mimeType));
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
