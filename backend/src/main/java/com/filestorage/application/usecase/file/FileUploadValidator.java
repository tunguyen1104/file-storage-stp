package com.filestorage.application.usecase.file;

import com.filestorage.infrastructure.config.ClamAvProperties;
import com.filestorage.infrastructure.config.UploadProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.tika.Tika;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.io.InputStream;

/**
 * Application-layer helper for upload requests.
 * <p>
 * Responsibilities:
 * <ol>
 *   <li><b>Tika detection</b> — detects the real MIME type from the first bytes of a stream
 *       to prevent clients from misrepresenting their file's content type.</li>
 *   <li><b>Scan routing</b> — determines whether a file must go through ClamAV or can be
 *       activated directly (files ≥ 50 MB or with non-scannable MIME types bypass scanning).</li>
 * </ol>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class FileUploadValidator {

    private final UploadProperties uploadProperties;
    private final ClamAvProperties clamAvProperties;
    private final Tika tika = new Tika();

    public String detectMime(InputStream inputStream, String declaredMimeType) {
        String result;
        try {
            String detected = tika.detect(inputStream);
            result = (detected != null && !detected.isBlank()) ? detected : normalise(declaredMimeType);
        } catch (IOException e) {
            log.warn("Tika MIME detection failed; falling back to declared type '{}': {}", declaredMimeType, e.getMessage());
            result = normalise(declaredMimeType);
        }
        return result;
    }

    public boolean shouldBypassScan(String mimeType, long sizeBytes) {
        // Guard: file exceeds ClamAV REST API size limit → bypass to prevent HTTP 413
        if (sizeBytes > clamAvProperties.getMaxScanFileSizeBytes()) {
            log.warn("Scan bypass (too large for ClamAV): size={} bytes — activating directly", sizeBytes);
            return true;
        }

        // Bypass: file is at or above the 50 MB scan threshold
        long scanThresholdBytes = uploadProperties.getVirusScan().getBypassScanSizeMb() * 1024L * 1024L;
        if (sizeBytes >= scanThresholdBytes) {
            log.debug("Scan bypass (file too large): size={}MB >= threshold={}MB",
                    sizeBytes / (1024L * 1024L), uploadProperties.getVirusScan().getBypassScanSizeMb());
            return true;
        }

        // Bypass: MIME type is not in the scan-required list
        String normalisedMime = normalise(mimeType);
        if (!uploadProperties.getVirusScan().getScanMimeTypes().contains(normalisedMime)) {
            log.debug("Scan bypass (non-scannable MIME type): mime='{}'", normalisedMime);
            return true;
        }

        return false;
    }

    // ─── Private helpers ──────────────────────────────────────────────────────

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
