package com.filestorage.domain.port.out;

import com.filestorage.adapter.out.persistence.entity.FileShare;
import com.filestorage.domain.model.enums.ShareStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShareRepositoryPort {

    FileShare save(FileShare share);

    Optional<FileShare> findById(UUID shareId);

    boolean existsActiveUserFileShare(UUID fileId, UUID userId);

    boolean existsActiveUserFolderShare(UUID folderId, UUID userId);

    boolean existsActiveTenantFileShare(UUID fileId, UUID tenantId);

    boolean existsActiveTenantFolderShare(UUID folderId, UUID tenantId);

    boolean existsActiveAllTenantsFileShare(UUID fileId);

    boolean existsActiveAllTenantsFolderShare(UUID folderId);

    Optional<FileShare> findActiveUserFileShare(UUID fileId, UUID userId);

    Optional<FileShare> findActiveUserFolderShare(UUID folderId, UUID userId);

    boolean hasActiveTenantFileShare(UUID fileId, UUID tenantId);

    boolean hasActiveTenantFolderShare(UUID folderId, UUID tenantId);

    boolean hasActiveAllTenantsFileShare(UUID fileId);

    boolean hasActiveAllTenantsFolderShare(UUID folderId);

    boolean hasActiveUserShareOnAnyAncestor(UUID folderId, UUID userId);

    boolean hasActiveTenantShareOnAnyAncestor(UUID folderId, UUID tenantId);

    boolean hasActiveAllTenantsShareOnAnyAncestor(UUID folderId);

    Page<FileShare> findBySharedBy(UUID userId, String resourceType, ShareStatus status, Pageable pageable);

    Page<FileShare> findSharesWithMe(UUID userId, UUID tenantId, String resourceType, Pageable pageable);

    Page<FileShare> findAdminShares(
            List<UUID> tenantIds,
            List<UUID> sharedByIds,
            String resourceType,
            List<ShareStatus> statuses,
            Pageable pageable
    );

    List<FileShare> findActiveSharesByUserScope(UUID userId, String scope);
}
