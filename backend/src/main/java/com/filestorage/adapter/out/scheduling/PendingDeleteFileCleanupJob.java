package com.filestorage.adapter.out.scheduling;

import com.filestorage.adapter.out.persistence.entity.File;
import com.filestorage.adapter.out.persistence.repository.FileJpaRepository;
import com.filestorage.application.usecase.file.FileHardDeleteSupport;
import com.filestorage.domain.model.enums.FileStatus;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Scheduled job that hard-deletes files that have been in PENDING_DELETE status
 * past their scheduledDeleteAt timestamp (default: 30 days after soft delete).
 * <p>
 * For each expired file: decrements user quota, removes share records,
 * marks status as DELETED, and deletes the object from storage.
 * <p>
 * Note: In a multi-instance deployment, consider adding ShedLock to ensure
 * only one node runs this job at a time.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class PendingDeleteFileCleanupJob {

    private final FileJpaRepository fileRepository;
    private final FileHardDeleteSupport hardDeleteSupport;

    @Scheduled(fixedDelay = 12, timeUnit = TimeUnit.HOURS)
    @SchedulerLock(name = "PendingDeleteFileCleanupJob", lockAtMostFor = "13h", lockAtLeastFor = "11h")
    @Transactional
    public void cleanupExpiredPendingDeleteFiles() {
        List<File> expiredFiles = fileRepository
                .findByStatusAndScheduledDeleteAtBefore(FileStatus.PENDING_DELETE, Instant.now());

        if (expiredFiles.isEmpty()) {
            return;
        }

        log.info("PendingDeleteFileCleanupJob: found {} file(s) past scheduledDeleteAt", expiredFiles.size());

        int succeeded = 0;
        for (File file : expiredFiles) {
            try {
                hardDeleteSupport.hardDelete(file);
                succeeded++;
                log.debug("PendingDeleteFileCleanupJob: hard deleted fileId={}", file.getId());
            } catch (Exception e) {
                log.warn("PendingDeleteFileCleanupJob: failed to hard delete fileId={}: {}",
                        file.getId(), e.getMessage());
            }
        }

        log.info("PendingDeleteFileCleanupJob: completed — {}/{} file(s) deleted",
                succeeded, expiredFiles.size());
    }
}
