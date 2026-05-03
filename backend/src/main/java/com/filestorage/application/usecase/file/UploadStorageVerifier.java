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

    public void verifySizeOrFail(File file, String storageKey) {
        ObjectStoragePort.ObjectMetadata actual = objectStoragePort.getObjectMetadata(storageKey);
        if (file.getSizeBytes() != null && actual.sizeBytes() != file.getSizeBytes()) {
            log.warn("Size mismatch for file {} — declared {} bytes, actual {} bytes",
                    file.getId(), file.getSizeBytes(), actual.sizeBytes());
            markFailedAndPurge(file, storageKey);
            throw new AppException(ErrorCode.FILE_METADATA_MISMATCH);
        }
    }

    public void verifyMimeOrFail(File file, String storageKey) {
        byte[] header = objectStoragePort.downloadPartial(storageKey, MIME_DETECTION_BYTES);
        try {
            fileUploadValidator.rejectIfMimeSpoofed(new ByteArrayInputStream(header), file.getMimeType());
        } catch (AppException ex) {
            log.warn("MIME spoofing detected for file {} — purging from storage", file.getId());
            markFailedAndPurge(file, storageKey);
            throw ex;
        }
    }

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

    // ─── Private helpers ──────────────────────────────────────────────────────

    private void markFailedAndPurge(File file, String storageKey) {
        file.setStatus(FileStatus.UPLOAD_FAILED);
        fileRepository.save(file);
        objectStoragePort.deleteObjectIfExists(storageKey);
    }
}

