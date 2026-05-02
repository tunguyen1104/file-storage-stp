package com.filestorage.adapter.out.persistence.repository;

import com.filestorage.adapter.out.persistence.entity.MultipartUploadSession;
import com.filestorage.domain.model.enums.MultipartUploadStatus;
import org.springframework.data.jpa.repository.JpaRepository;
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
     * Load session by uploadId + owner check — used by all owner-only operations
     * (get part URL, complete part, complete multipart, get session).
     * JOIN FETCH initiatedBy to avoid N+1 on ownership checks.
     */
    @Query("""
            SELECT s FROM MultipartUploadSession s
            JOIN FETCH s.initiatedBy
            WHERE s.uploadId = :uploadId
              AND s.initiatedBy.id = :userId
            """)
    Optional<MultipartUploadSession> findByUploadIdAndInitiatedById(
            @Param("uploadId") String uploadId,
            @Param("userId") UUID userId);

    /**
     * Load session by uploadId scoped to a tenant (via the linked file).
     * Used by abort — tenant isolation enforced at query level
     * JOIN FETCH initiatedBy
     */
    @Query("""
            SELECT s FROM MultipartUploadSession s
            JOIN FETCH s.initiatedBy
            JOIN FETCH s.file f
            JOIN FETCH f.tenant
            WHERE s.uploadId = :uploadId
              AND f.tenant.id = :tenantId
            """)
    Optional<MultipartUploadSession> findByUploadIdAndFileTenantId(
            @Param("uploadId") String uploadId,
            @Param("tenantId") UUID tenantId);

    /**
     * Used by the background cleanup job to find sessions that have passed their
     * expiry time and are still IN_PROGRESS.
     */
    List<MultipartUploadSession> findByStatusAndExpiresAtBefore(
            MultipartUploadStatus status, Instant cutoff);
}
