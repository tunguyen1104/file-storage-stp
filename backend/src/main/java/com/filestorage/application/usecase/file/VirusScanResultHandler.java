package com.filestorage.application.usecase.file;

import com.filestorage.adapter.out.persistence.entity.File;
import com.filestorage.adapter.out.persistence.repository.FileJpaRepository;
import com.filestorage.adapter.out.persistence.repository.UserJpaRepository;
import com.filestorage.domain.event.VirusScanCompletedEvent;
import com.filestorage.domain.model.enums.FileStatus;
import com.filestorage.domain.port.out.ObjectStoragePort;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Application service that processes the result of a completed virus scan.
 * <p>
 * When clean: SCANNING → ACTIVE, quota is updated.<br>
 * When infected: SCANNING → INFECTED, object is deleted from MinIO.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class VirusScanResultHandler {

    private final FileJpaRepository fileRepository;
    private final UserJpaRepository userRepository;
    private final ObjectStoragePort objectStoragePort;

    @Transactional
    public void handle(VirusScanCompletedEvent event) {
        File file = fileRepository.findByIdWithUploader(event.fileId())
                .orElseThrow(() -> new AppException(ErrorCode.FILE_NOT_FOUND));

        if (file.getStatus() != FileStatus.SCANNING) {
            log.warn("VirusScanResultHandler: file {} is not in SCANNING state (actual: {}), skipping",
                    event.fileId(), file.getStatus());
            return;
        }

        if (event.infected()) {
            handleInfected(file);
        } else {
            handleClean(file);
        }
    }

    private void handleInfected(File file) {
        log.warn("Virus detected in file {}, deleting from storage", file.getId());
        file.setStatus(FileStatus.INFECTED);
        fileRepository.save(file);

        // Delete the physical object — do not update quota (file was never counted)
        objectStoragePort.deleteObjectIfExists(file.getStorageKey());
    }

    void handleClean(File file) {
        long sizeBytes = file.getSizeBytes() == null ? 0L : file.getSizeBytes();

        // Single atomic UPDATE: increments quota only if the result stays within the limit.
        // Returns 0 when quota would be exceeded (e.g. admin reduced quota while file was in-flight).
        int updated = userRepository.incrementStorageUsedIfQuotaAllows(
                file.getUploadedBy().getId(), sizeBytes);

        if (updated == 0) {
            log.warn("File {} would exceed quota after scan — marking UPLOAD_FAILED and purging", file.getId());
            file.setStatus(FileStatus.UPLOAD_FAILED);
            fileRepository.save(file);
            objectStoragePort.deleteObjectIfExists(file.getStorageKey());
            return;
        }

        file.setStatus(FileStatus.ACTIVE);
        fileRepository.save(file);

        log.info("File {} passed virus scan — status set to ACTIVE, quota updated (+{} bytes)", file.getId(), sizeBytes);
    }
}
