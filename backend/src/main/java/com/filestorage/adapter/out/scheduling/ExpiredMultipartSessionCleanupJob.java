package com.filestorage.adapter.out.scheduling;

import com.filestorage.adapter.out.persistence.entity.MultipartUploadSession;
import com.filestorage.adapter.out.persistence.repository.FileJpaRepository;
import com.filestorage.adapter.out.persistence.repository.MultipartUploadSessionJpaRepository;
import com.filestorage.domain.model.enums.FileStatus;
import com.filestorage.domain.model.enums.MultipartUploadStatus;
import com.filestorage.domain.port.out.CachePort;
import com.filestorage.domain.port.out.ObjectStoragePort;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

/**
 * Scheduled job that cleans up expired multipart upload sessions.
 * <p>
 * A session is considered expired when {@code expiresAt < now} and its status is still
 * {@code IN_PROGRESS}, meaning the client did not complete or abort the upload within 24 h.
 * <p>
 * For each expired session the job:
 * <ol>
 *   <li>Calls MinIO to abort the multipart upload (removing all orphaned part chunks).</li>
 *   <li>Transitions the associated {@code files} row to {@code UPLOAD_FAILED}.</li>
 *   <li>Transitions the session to {@code ABORTED}.</li>
 *   <li>Evicts the session from the Redis cache.</li>
 * </ol>
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ExpiredMultipartSessionCleanupJob {

    private final MultipartUploadSessionJpaRepository sessionRepository;
    private final FileJpaRepository fileRepository;
    private final ObjectStoragePort objectStoragePort;
    private final CachePort cachePort;

    @Scheduled(cron = "0 0 */6 * * *")
    @Transactional
    public void cleanupExpiredSessions() {
        List<MultipartUploadSession> expired = sessionRepository
                .findByStatusAndExpiresAtBefore(MultipartUploadStatus.IN_PROGRESS, Instant.now());

        if (expired.isEmpty()) {
            return;
        }

        log.info("ExpiredMultipartSessionCleanupJob: found {} expired IN_PROGRESS session(s)", expired.size());

        for (MultipartUploadSession session : expired) {
            // Abort on MinIO — best-effort, do not fail the loop on MinIO errors
            try {
                objectStoragePort.abortMultipartUpload(session.getUploadId(), session.getObjectKey());
            } catch (Exception e) {
                log.warn("ExpiredMultipartSessionCleanupJob: MinIO abort failed for uploadId={}: {}",
                        session.getUploadId(), e.getMessage());
            }

            // Transition file status
            com.filestorage.adapter.out.persistence.entity.File file = session.getFile();
            file.setStatus(FileStatus.UPLOAD_FAILED);
            fileRepository.save(file);

            // Transition session status
            session.setStatus(MultipartUploadStatus.ABORTED);
            sessionRepository.save(session);

            // Evict Redis cache
            cachePort.evictMultipartSession(session.getUploadId());

            log.debug("ExpiredMultipartSessionCleanupJob: aborted session uploadId={} fileId={}",
                    session.getUploadId(), file.getId());
        }

        log.info("ExpiredMultipartSessionCleanupJob: completed cleanup of {} session(s)", expired.size());
    }
}
