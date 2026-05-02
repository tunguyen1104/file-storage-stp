package com.filestorage.adapter.out.scheduling;

import com.filestorage.adapter.out.persistence.entity.File;
import com.filestorage.adapter.out.persistence.repository.FileJpaRepository;
import com.filestorage.domain.model.enums.FileStatus;
import com.filestorage.domain.port.out.ObjectStoragePort;
import com.filestorage.infrastructure.config.UploadProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Scheduled job that marks stale PENDING files as UPLOAD_FAILED and cleans up
 * any partial objects that may have been uploaded to MinIO.
 * <p>
 * A file is considered stale when it has remained in PENDING status for longer
 * than {@code app.upload.pending-cleanup-threshold-hours} (default: 1 hour),
 * indicating the client never completed the upload or the presigned URL expired.
 * <p>
 * Note: In a multi-instance deployment, consider adding ShedLock
 * ({@code net.javacrumbs.shedlock:shedlock-spring}) to ensure only one node runs
 * this job at a time.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PendingFileCleanupJob {

    private final FileJpaRepository fileRepository;
    private final ObjectStoragePort objectStoragePort;
    private final UploadProperties uploadProperties;

    /**
     * Runs every 15 minutes. Marks files that have been PENDING for longer than
     * the configured threshold as UPLOAD_FAILED and attempts to remove any
     * partial object from MinIO.
     */
    @Scheduled(fixedDelay = 15, timeUnit = TimeUnit.MINUTES)
    @Transactional
    public void cleanupStalePendingFiles() {
        Instant cutoff = Instant.now().minus(uploadProperties.getPendingCleanupThresholdHours(), ChronoUnit.HOURS);
        List<File> staleFiles = fileRepository.findByStatusAndCreatedAtBefore(FileStatus.PENDING, cutoff);

        if (staleFiles.isEmpty()) {
            return;
        }

        log.info("PendingFileCleanupJob: found {} stale PENDING file(s) created before {}", staleFiles.size(), cutoff);

        for (File file : staleFiles) {
            file.setStatus(FileStatus.UPLOAD_FAILED);
            fileRepository.save(file);

            // Best-effort: delete any partial object that was PUT to MinIO before the upload timed out
            try {
                objectStoragePort.deleteObjectIfExists(file.getStorageKey());
            } catch (Exception e) {
                log.warn("PendingFileCleanupJob: failed to delete MinIO object for fileId={} storageKey={}: {}",
                        file.getId(), file.getStorageKey(), e.getMessage());
            }

            log.debug("PendingFileCleanupJob: marked fileId={} as UPLOAD_FAILED", file.getId());
        }

        log.info("PendingFileCleanupJob: completed cleanup of {} stale file(s)", staleFiles.size());
    }
}
