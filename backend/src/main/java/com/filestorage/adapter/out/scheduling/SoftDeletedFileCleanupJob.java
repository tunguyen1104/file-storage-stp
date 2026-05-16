package com.filestorage.adapter.out.scheduling;

import com.filestorage.adapter.out.persistence.entity.File;
import com.filestorage.adapter.out.persistence.entity.User;
import com.filestorage.adapter.out.persistence.repository.FileJpaRepository;
import com.filestorage.adapter.out.persistence.repository.FileShareJpaRepository;
import com.filestorage.adapter.out.persistence.repository.UserJpaRepository;
import com.filestorage.domain.model.enums.FileStatus;
import com.filestorage.domain.port.out.ObjectStoragePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
@Slf4j
@RequiredArgsConstructor
public class SoftDeletedFileCleanupJob {

    private final FileJpaRepository fileRepository;
    private final FileShareJpaRepository fileShareRepository;
    private final UserJpaRepository userRepository;
    private final ObjectStoragePort objectStoragePort;

    @Scheduled(cron = "${app.scheduling.soft-delete-cleanup-cron:0 0 2 * * *}")
    @Transactional
    public void purgeExpiredSoftDeletedFiles() {
        List<File> expired = fileRepository
                .findByStatusAndScheduledDeleteAtBefore(FileStatus.PENDING_DELETE, Instant.now());

        if (expired.isEmpty()) {
            return;
        }

        log.info("SoftDeletedFileCleanupJob: found {} PENDING_DELETE file(s) due for purge", expired.size());

        int purged = 0;
        for (File file : expired) {
            try {
                purgeFile(file);
                purged++;
            } catch (Exception e) {
                // Defensive: do not let a single failing row abort the whole batch.
                log.warn("SoftDeletedFileCleanupJob: failed to purge fileId={} storageKey={}: {}",
                        file.getId(), file.getStorageKey(), e.getMessage(), e);
            }
        }

        log.info("SoftDeletedFileCleanupJob: purged {}/{} file(s)", purged, expired.size());
    }

    private void purgeFile(File file) {
        // Re-fetch with a PESSIMISTIC_WRITE lock to guard against a concurrent
        // RestoreFileService call that may have already flipped status back to ACTIVE.
        // If the status changed, skip this file silently.
        File locked = fileRepository
                .findByIdAndStatusForUpdate(file.getId(), FileStatus.PENDING_DELETE)
                .orElse(null);
        if (locked == null) {
            log.info("SoftDeletedFileCleanupJob: skipping fileId={} — status changed before purge",
                    file.getId());
            return;
        }

        // 1. Remove share records referencing this file.
        fileShareRepository.deleteByFileId(locked.getId());

        // 2. Best-effort delete of the primary object and the thumbnail (if any).
        safeDeleteStorage(locked.getStorageKey());
        if (locked.getThumbnailKey() != null && !locked.getThumbnailKey().isBlank()) {
            safeDeleteStorage(locked.getThumbnailKey());
        }

        // 3. Release the uploader's quota.
        decrementUploaderUsage(locked);

        // 4. Mark the row DELETED (rows are retained for audit; never hard-deleted).
        locked.setStatus(FileStatus.DELETED);
        locked.setScheduledDeleteAt(null);
        fileRepository.save(locked);

        log.debug("SoftDeletedFileCleanupJob: purged fileId={} storageKey={}",
                locked.getId(), locked.getStorageKey());
    }

    private void safeDeleteStorage(String storageKey) {
        try {
            objectStoragePort.deleteObjectIfExists(storageKey);
        } catch (Exception e) {
            log.warn("SoftDeletedFileCleanupJob: MinIO delete failed for key={}: {}",
                    storageKey, e.getMessage());
        }
    }

    private void decrementUploaderUsage(File file) {
        User uploader = file.getUploadedBy();
        if (uploader == null) {
            return;
        }

        long updated = Math.max(0L, uploader.getStorageUsedBytes() - file.getSizeBytes());
        uploader.setStorageUsedBytes(updated);
        userRepository.save(uploader);
    }
}
