package com.filestorage.adapter.out.persistence.repository;

import com.filestorage.adapter.out.persistence.entity.MultipartUploadSession;
import com.filestorage.domain.model.enums.MultipartUploadStatus;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface MultipartUploadSessionJpaRepository extends JpaRepository<MultipartUploadSession, String> {

    /**
     * Load session with PESSIMISTIC_WRITE lock — used only by CompletePartUploadService
     * to prevent lost-update race condition when multiple parts complete concurrently.
     * Two concurrent calls for the same uploadId will serialize here.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT s FROM MultipartUploadSession s
            JOIN FETCH s.initiatedBy
            JOIN FETCH s.file
            WHERE s.uploadId = :uploadId
              AND s.initiatedBy.id = :userId
            """)
    Optional<MultipartUploadSession> findByUploadIdAndInitiatedByIdForUpdate(
            @Param("uploadId") String uploadId,
            @Param("userId") UUID userId);

    /**
     * Load session by uploadId + owner check — used by all owner-only operations
     * (get part URL, complete part, complete multipart, get session).
     * JOIN FETCH initiatedBy to avoid N+1 on ownership checks.
     */
    @Query("""
            SELECT s FROM MultipartUploadSession s
            JOIN FETCH s.initiatedBy
            JOIN FETCH s.file
            WHERE s.uploadId = :uploadId
              AND s.initiatedBy.id = :userId
            """)
    Optional<MultipartUploadSession> findByUploadIdAndInitiatedById(
            @Param("uploadId") String uploadId,
            @Param("userId") UUID userId);

    /**
     * Load session with PESSIMISTIC_WRITE lock scoped to a tenant — used by
     * AbortMultipartUploadService to prevent complete+abort race condition.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT s FROM MultipartUploadSession s
            JOIN FETCH s.initiatedBy
            JOIN FETCH s.file f
            JOIN FETCH f.tenant
            WHERE s.uploadId = :uploadId
              AND f.tenant.id = :tenantId
            """)
    Optional<MultipartUploadSession> findByUploadIdAndFileTenantIdForUpdate(
            @Param("uploadId") String uploadId,
            @Param("tenantId") UUID tenantId);

    /**
     * Used by the background cleanup job to find sessions that have passed their
     * expiry time and are still IN_PROGRESS.
     */
    List<MultipartUploadSession> findByStatusAndExpiresAtBefore(
            MultipartUploadStatus status, Instant cutoff);
}
