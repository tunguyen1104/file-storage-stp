package com.filestorage.application.usecase.file;

import com.filestorage.adapter.out.persistence.entity.File;
import com.filestorage.adapter.out.persistence.repository.FileJpaRepository;
import com.filestorage.domain.event.VirusScanRequestedEvent;
import com.filestorage.domain.model.enums.FileStatus;
import com.filestorage.domain.port.out.ObjectStoragePort;
import com.filestorage.domain.port.out.VirusScanEventPublisherPort;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.io.ByteArrayInputStream;
import java.util.UUID;

/**
 * Shared post-upload verification steps used by both presigned and multipart upload flows.
 * <p>
 * Responsibilities:
 * <ol>
 *   <li><b>Size verification</b> — compares declared size against the actual object size
 *       reported by MinIO to prevent quota fraud.</li>
 *   <li><b>MIME spoof detection</b> — downloads only the first 8 KB and uses Apache Tika
 *       to detect the real content type, rejecting files that disguise their true format.</li>
 *   <li><b>Virus scan routing</b> — either bypasses ClamAV for large trusted-media files
 *       and activates directly, or transitions to SCANNING and publishes an async scan event.</li>
 * </ol>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class UploadStorageVerifier {

    /** Number of bytes fetched from MinIO for Tika magic-byte detection. */
    private static final int MIME_DETECTION_BYTES = 8_192;

    private final FileJpaRepository fileRepository;
    private final ObjectStoragePort objectStoragePort;
    private final VirusScanEventPublisherPort virusScanEventPublisherPort;
    private final FileUploadValidator fileUploadValidator;
    private final VirusScanResultHandler virusScanResultHandler;
    private final FailedUploadPurger failedUploadPurger;

    /**
     * Verifies that the actual object size stored in MinIO matches the declared size.
     * Marks the file as {@link FileStatus#UPLOAD_FAILED} and purges the object on mismatch.
     *
     * @param file       the file entity whose declared size will be checked
     * @param storageKey object path inside the bucket
     * @throws AppException with {@link ErrorCode#FILE_METADATA_MISMATCH} on size mismatch
     */
    public void verifySizeOrFail(File file, String storageKey) {
        ObjectStoragePort.ObjectMetadata actual = objectStoragePort.getObjectMetadata(storageKey);
        if (file.getSizeBytes() != null && actual.sizeBytes() != file.getSizeBytes()) {
            log.warn("Size mismatch for file {} — declared {} bytes, actual {} bytes",
                    file.getId(), file.getSizeBytes(), actual.sizeBytes());
            failedUploadPurger.markFailedAndPurge(file, storageKey);
            throw new AppException(ErrorCode.FILE_METADATA_MISMATCH);
        }
    }

    /**
     * Downloads the first {@value #MIME_DETECTION_BYTES} bytes from MinIO, uses Apache Tika
     * to detect the real MIME type, validates it against the whitelist, and then
     * <strong>updates {@code file.mimeType} with the Tika-detected type</strong>.
     * <p>
     * Updating the stored type is critical: subsequent decisions such as
     * {@link FileUploadValidator#shouldBypassScan} must operate on the <em>actual</em>
     * content type — not the client-declared value — to prevent a client from declaring
     * {@code video/mp4} on a large PDF/DOCX and bypassing ClamAV.
     *
     * @param file       the file entity whose MIME type will be verified and corrected
     * @param storageKey object path inside the bucket
     * @throws AppException with {@link ErrorCode#UNSUPPORTED_FILE_TYPE} on MIME spoof
     */
    public void verifyMimeOrFail(File file, String storageKey) {
        byte[] header = objectStoragePort.downloadPartial(storageKey, MIME_DETECTION_BYTES);
        try {
            String detectedMime = fileUploadValidator.detectAndValidateMime(
                    new ByteArrayInputStream(header), file.getMimeType());
            // Overwrite the client-declared type with the Tika-verified type so all
            // downstream logic (bypass check, DB record) reflects the real content.
            if (!detectedMime.equalsIgnoreCase(file.getMimeType())) {
                log.info("Correcting MIME type for file {} from '{}' to Tika-detected '{}'",
                        file.getId(), file.getMimeType(), detectedMime);
                file.setMimeType(detectedMime);
            }
        } catch (AppException ex) {
            log.warn("MIME spoofing detected for file {} — purging from storage", file.getId());
            failedUploadPurger.markFailedAndPurge(file, storageKey);
            throw ex;
        }
    }

    /**
     * Routes the file to ClamAV scanning or activates it directly if eligible for scan bypass.
     * <p>
     * <ul>
     *   <li>Bypass path: transitions to SCANNING, calls {@link VirusScanResultHandler#handleClean}
     *       synchronously, and returns {@link FileStatus#ACTIVE}.</li>
     *   <li>Standard path: transitions to SCANNING, publishes an async
     *       {@link com.filestorage.domain.event.VirusScanRequestedEvent}, and returns
     *       {@link FileStatus#SCANNING}.</li>
     * </ul>
     *
     * @param file     the file entity to route
     * @param tenantId caller's tenant ID (forwarded in the scan event)
     * @param userId   caller's user ID (forwarded in the scan event)
     * @return the resulting {@link FileStatus} — either {@code ACTIVE} or {@code SCANNING}
     */
    public FileStatus routeToScanOrActivate(File file, UUID tenantId, UUID userId) {
        file.setStatus(FileStatus.SCANNING);
        fileRepository.save(file);

        long sizeBytes = file.getSizeBytes() == null ? 0L : file.getSizeBytes();

        if (fileUploadValidator.shouldBypassScan(file.getMimeType(), sizeBytes)) {
            log.info("File {} qualifies for scan bypass (mime={}, size={} bytes) — activating directly",
                    file.getId(), file.getMimeType(), sizeBytes);
            virusScanResultHandler.handleClean(file);
            return FileStatus.ACTIVE;
        }

        virusScanEventPublisherPort.publishVirusScanRequested(new VirusScanRequestedEvent(
                file.getId(),
                file.getStorageKey(),
                tenantId,
                userId
        ));

        return FileStatus.SCANNING;
    }
}
