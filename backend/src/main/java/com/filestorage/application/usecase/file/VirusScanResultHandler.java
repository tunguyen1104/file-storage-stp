package com.filestorage.application.usecase.file;

import com.filestorage.adapter.out.persistence.entity.File;
import com.filestorage.adapter.out.persistence.repository.FileJpaRepository;
import com.filestorage.adapter.out.persistence.repository.UserJpaRepository;
import com.filestorage.application.usecase.indexing.AfterCommitIndexingPublisher;
import com.filestorage.domain.event.FileIndexEvent;
import com.filestorage.domain.event.FileStatusChangedEvent;
import com.filestorage.domain.event.VirusScanCompletedEvent;
import com.filestorage.domain.model.audit.AuditAction;
import com.filestorage.domain.model.enums.FileStatus;
import com.filestorage.domain.port.in.audit.LogAuditEventUseCase;
import com.filestorage.domain.port.in.audit.LogAuditEventUseCase.AuditEvent;
import com.filestorage.domain.port.out.FileStatusEventPublisherPort;
import com.filestorage.domain.port.out.ObjectStoragePort;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;


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
    private final FileStatusEventPublisherPort fileStatusEventPublisher;
    private final AfterCommitIndexingPublisher indexingPublisher;
    private final LogAuditEventUseCase auditLogger;

    @Transactional
    public void handle(VirusScanCompletedEvent event) {
        File file = fileRepository.findByIdWithUploaderAndTenant(event.fileId())
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

        publishStatusChanged(file, FileStatus.INFECTED);
        auditSystemFileEvent(file, AuditAction.VIRUS_DETECTED, Map.of("scanner", "clamav"));
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
            publishStatusChanged(file, FileStatus.UPLOAD_FAILED);
            auditSystemFileEvent(file, AuditAction.UPLOAD_FAILED, Map.of(
                    "stage", "virus_scan",
                    "error_code", "QUOTA_EXCEEDED_POST_SCAN"
            ));
            return;
        }

        file.setStatus(FileStatus.ACTIVE);
        fileRepository.save(file);

        log.info("File {} passed virus scan — status set to ACTIVE, quota updated (+{} bytes)", file.getId(), sizeBytes);
        publishStatusChanged(file, FileStatus.ACTIVE);

        // Single publish point for indexing across BOTH upload flows (presigned + multipart):
        // both go through virus scan before reaching ACTIVE. ConfirmUploadService and
        // CompleteMultipartUploadService deliberately do NOT publish — adding indexing calls
        // there would double-fire for every upload.
        indexingPublisher.publishFileUploaded(new FileIndexEvent(
                file.getId(),
                file.getTenant().getId(),
                file.getUploadedBy().getId(),
                FileIndexEvent.Action.UPSERT_CONTENT
        ));
        // INFECTED / UPLOAD_FAILED: no DELETE event — the file was never indexed,
        // so there is no ES doc to remove.
    }

    private void publishStatusChanged(File file, FileStatus status) {
        fileStatusEventPublisher.publishStatusChanged(new FileStatusChangedEvent(
                file.getId(),
                file.getTenant().getId(),
                file.getUploadedBy().getId(),
                status
        ));
    }

    private void auditSystemFileEvent(File file, AuditAction action, Map<String, Object> details) {
        Map<String, Object> extra = new HashMap<>(details);
        extra.put("actor_role", "SYSTEM");
        extra.put("actor_tenant_id", file.getTenant().getId().toString());
        auditLogger.logAsync(new AuditEvent(
                action,
                file.getTenant().getId(),
                null,
                file.getId(),
                "SYSTEM",
                null,
                null,
                null,
                extra
        ));
    }
}
