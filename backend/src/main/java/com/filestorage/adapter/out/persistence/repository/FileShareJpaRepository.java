package com.filestorage.adapter.out.persistence.repository;

import com.filestorage.adapter.out.persistence.entity.FileShare;
import com.filestorage.domain.model.enums.ShareStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FileShareJpaRepository extends JpaRepository<FileShare, UUID> {

    void deleteByFileId(UUID fileId);

    boolean existsByFileIdAndSharedWithUserIdAndStatus(UUID fileId, UUID userId, ShareStatus status);

    boolean existsByFolderIdAndSharedWithUserIdAndStatus(UUID folderId, UUID userId, ShareStatus status);

    boolean existsByFileIdAndSharedWithTenantIdAndStatus(UUID fileId, UUID tenantId, ShareStatus status);

    boolean existsByFolderIdAndSharedWithTenantIdAndStatus(UUID folderId, UUID tenantId, ShareStatus status);

    boolean existsByFileIdAndSharedWithUserIdAndStatusAndExpiresAtAfter(
            UUID fileId,
            UUID userId,
            ShareStatus status,
            Instant now
    );

    boolean existsByFolderIdAndSharedWithUserIdAndStatusAndExpiresAtAfter(
            UUID folderId,
            UUID userId,
            ShareStatus status,
            Instant now
    );

    boolean existsByFileIdAndSharedWithTenantIdAndStatusAndExpiresAtAfter(
            UUID fileId,
            UUID tenantId,
            ShareStatus status,
            Instant now
    );

    boolean existsByFolderIdAndSharedWithTenantIdAndStatusAndExpiresAtAfter(
            UUID folderId,
            UUID tenantId,
            ShareStatus status,
            Instant now
    );

    Optional<FileShare> findByFileIdAndSharedWithUserIdAndStatusAndExpiresAtAfter(
            UUID fileId,
            UUID userId,
            ShareStatus status,
            Instant now
    );

    Optional<FileShare> findByFolderIdAndSharedWithUserIdAndStatusAndExpiresAtAfter(
            UUID folderId,
            UUID userId,
            ShareStatus status,
            Instant now
    );

    @Query("""
            SELECT fs FROM FileShare fs
            WHERE fs.sharedBy = :sharedBy
              AND (:status IS NULL OR fs.status = :status)
              AND (:resourceType IS NULL
                   OR (:resourceType = 'file' AND fs.fileId IS NOT NULL)
                   OR (:resourceType = 'folder' AND fs.folderId IS NOT NULL))
            """)
    Page<FileShare> findBySharedByAndFilters(
            @Param("sharedBy") UUID sharedBy,
            @Param("resourceType") String resourceType,
            @Param("status") ShareStatus status,
            Pageable pageable
    );

    @Query("""
            SELECT fs FROM FileShare fs
            WHERE (fs.sharedWithUserId = :userId OR fs.sharedWithTenantId = :tenantId)
              AND fs.status = :status
              AND fs.expiresAt > :now
              AND (:resourceType IS NULL
                   OR (:resourceType = 'file' AND fs.fileId IS NOT NULL)
                   OR (:resourceType = 'folder' AND fs.folderId IS NOT NULL))
            """)
    Page<FileShare> findSharesWithMe(
            @Param("userId") UUID userId,
            @Param("tenantId") UUID tenantId,
            @Param("resourceType") String resourceType,
            @Param("status") ShareStatus status,
            @Param("now") Instant now,
            Pageable pageable
    );

    List<FileShare> findBySharedByAndStatusAndExpiresAtAfter(
            UUID userId,
            ShareStatus status,
            Instant now
    );

    List<FileShare> findBySharedWithUserIdAndStatusAndExpiresAtAfter(
            UUID userId,
            ShareStatus status,
            Instant now
    );

    @Query(value = """
            WITH RECURSIVE ancestors AS (
                SELECT id, parent_id FROM folders WHERE id = :folderId
                UNION ALL
                SELECT f.id, f.parent_id FROM folders f
                INNER JOIN ancestors a ON f.id = a.parent_id
            )
            SELECT EXISTS (
                SELECT 1 FROM file_shares fs
                JOIN ancestors a ON fs.folder_id = a.id
                WHERE fs.shared_with_user_id = :userId
                  AND fs.status = 'ACTIVE'
                  AND fs.expires_at > NOW()
            )
            """, nativeQuery = true)
    boolean hasActiveUserShareOnAnyAncestor(
            @Param("folderId") UUID folderId,
            @Param("userId") UUID userId
    );

    @Query(value = """
            WITH RECURSIVE ancestors AS (
                SELECT id, parent_id FROM folders WHERE id = :folderId
                UNION ALL
                SELECT f.id, f.parent_id FROM folders f
                INNER JOIN ancestors a ON f.id = a.parent_id
            )
            SELECT EXISTS (
                SELECT 1 FROM file_shares fs
                JOIN ancestors a ON fs.folder_id = a.id
                WHERE fs.shared_with_tenant_id = :tenantId
                  AND fs.status = 'ACTIVE'
                  AND fs.expires_at > NOW()
            )
            """, nativeQuery = true)
    boolean hasActiveTenantShareOnAnyAncestor(
            @Param("folderId") UUID folderId,
            @Param("tenantId") UUID tenantId
    );
}
