package com.filestorage.application.usecase.file;

import com.filestorage.adapter.out.persistence.entity.File;
import com.filestorage.adapter.out.persistence.repository.FileJpaRepository;
import com.filestorage.domain.event.VirusScanRequestedEvent;
import com.filestorage.domain.model.enums.FileStatus;
import com.filestorage.domain.model.file.ConfirmUploadView;
import com.filestorage.domain.port.in.file.ConfirmUploadUseCase;
import com.filestorage.domain.port.out.ObjectStoragePort;
import com.filestorage.domain.port.out.VirusScanEventPublisherPort;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ConfirmUploadService implements ConfirmUploadUseCase {

    private final FileJpaRepository fileRepository;
    private final FileSupport fileSupport;
    private final ObjectStoragePort objectStoragePort;
    private final VirusScanEventPublisherPort virusScanEventPublisherPort;

    @Override
    @Transactional
    public ConfirmUploadView execute(ConfirmUploadCommand command) {
        File file = fileRepository.findByIdAndTenantId(command.fileId(), command.tenantId())
                .orElseThrow(() -> new AppException(ErrorCode.FILE_NOT_FOUND));

        // Only the owner may confirm
        if (!file.getUploadedBy().getId().equals(command.userId())) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        // File must be in PENDING state
        if (file.getStatus() != FileStatus.PENDING) {
            throw new AppException(ErrorCode.INVALID_FILE_STATUS);
        }

        String storageKey = file.getStorageKey();

        // Verify the object actually exists in MinIO
        if (!objectStoragePort.objectExists(storageKey)) {
            file.setStatus(FileStatus.UPLOAD_FAILED);
            fileRepository.save(file);
            throw new AppException(ErrorCode.FILE_NOT_FOUND_ON_STORAGE);
        }

        // Verify metadata matches what was declared at initiation (security: prevents quota fraud)
        ObjectStoragePort.ObjectMetadata actual = objectStoragePort.getObjectMetadata(storageKey);
        if (!metadataMatches(file, actual)) {
            file.setStatus(FileStatus.UPLOAD_FAILED);
            fileRepository.save(file);
            throw new AppException(ErrorCode.FILE_METADATA_MISMATCH);
        }

        // Transition to SCANNING and publish async virus-scan task
        file.setStatus(FileStatus.SCANNING);
        fileRepository.save(file);

        virusScanEventPublisherPort.publishVirusScanRequested(new VirusScanRequestedEvent(
                file.getId(),
                storageKey,
                file.getTenant().getId(),
                file.getUploadedBy().getId()
        ));

        return new ConfirmUploadView(file.getId(), FileStatus.SCANNING);
    }

    private boolean metadataMatches(File file, ObjectStoragePort.ObjectMetadata actual) {
        if (file.getSizeBytes() != null && actual.sizeBytes() != file.getSizeBytes()) {
            return false;
        }
        return file.getMimeType() == null || normalizeContentType(file.getMimeType())
                .equalsIgnoreCase(normalizeContentType(actual.contentType()));
    }

    /** Strips parameters (e.g. {@code charset=utf-8}) from a MIME type for comparison. */
    private static String normalizeContentType(String contentType) {
        if (contentType == null) {
            return "";
        }
        int semicolon = contentType.indexOf(';');
        return semicolon >= 0 ? contentType.substring(0, semicolon).trim() : contentType.trim();
    }
}
