package com.filestorage.adapter.out.persistence.adapter;

import com.filestorage.adapter.out.persistence.entity.FileShare;
import com.filestorage.adapter.out.persistence.repository.FileShareJpaRepository;
import com.filestorage.domain.model.enums.ShareStatus;
import com.filestorage.domain.port.out.ShareRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Component
@RequiredArgsConstructor
public class SharePersistenceAdapter implements ShareRepositoryPort {

    private static final String SCOPE_GRANTED = "GRANTED";
    private static final String SCOPE_RECEIVED = "RECEIVED";
    private static final String SCOPE_BOTH = "BOTH";

    private final FileShareJpaRepository shareRepository;

    @Override
    public FileShare save(FileShare share) {
        return shareRepository.save(share);
    }

    @Override
    public Optional<FileShare> findById(UUID shareId) {
        return shareRepository.findById(shareId);
    }

    @Override
    public boolean existsActiveUserFileShare(UUID fileId, UUID userId) {
        return shareRepository.existsByFileIdAndSharedWithUserIdAndStatus(fileId, userId, ShareStatus.ACTIVE);
    }

    @Override
    public boolean existsActiveUserFolderShare(UUID folderId, UUID userId) {
        return shareRepository.existsByFolderIdAndSharedWithUserIdAndStatus(folderId, userId, ShareStatus.ACTIVE);
    }

    @Override
    public boolean existsActiveTenantFileShare(UUID fileId, UUID tenantId) {
        return shareRepository.existsByFileIdAndSharedWithTenantIdAndStatus(fileId, tenantId, ShareStatus.ACTIVE);
    }

    @Override
    public boolean existsActiveTenantFolderShare(UUID folderId, UUID tenantId) {
        return shareRepository.existsByFolderIdAndSharedWithTenantIdAndStatus(folderId, tenantId, ShareStatus.ACTIVE);
    }

    @Override
    public Optional<FileShare> findActiveUserFileShare(UUID fileId, UUID userId) {
        return shareRepository.findByFileIdAndSharedWithUserIdAndStatusAndExpiresAtAfter(
                fileId,
                userId,
                ShareStatus.ACTIVE,
                Instant.now()
        );
    }

    @Override
    public Optional<FileShare> findActiveUserFolderShare(UUID folderId, UUID userId) {
        return shareRepository.findByFolderIdAndSharedWithUserIdAndStatusAndExpiresAtAfter(
                folderId,
                userId,
                ShareStatus.ACTIVE,
                Instant.now()
        );
    }

    @Override
    public boolean hasActiveTenantFileShare(UUID fileId, UUID tenantId) {
        return shareRepository.existsByFileIdAndSharedWithTenantIdAndStatusAndExpiresAtAfter(
                fileId,
                tenantId,
                ShareStatus.ACTIVE,
                Instant.now()
        );
    }

    @Override
    public boolean hasActiveTenantFolderShare(UUID folderId, UUID tenantId) {
        return shareRepository.existsByFolderIdAndSharedWithTenantIdAndStatusAndExpiresAtAfter(
                folderId,
                tenantId,
                ShareStatus.ACTIVE,
                Instant.now()
        );
    }

    @Override
    public boolean hasActiveUserShareOnAnyAncestor(UUID folderId, UUID userId) {
        return shareRepository.hasActiveUserShareOnAnyAncestor(folderId, userId);
    }

    @Override
    public boolean hasActiveTenantShareOnAnyAncestor(UUID folderId, UUID tenantId) {
        return shareRepository.hasActiveTenantShareOnAnyAncestor(folderId, tenantId);
    }

    @Override
    public Page<FileShare> findBySharedBy(UUID userId, String resourceType, ShareStatus status, Pageable pageable) {
        return shareRepository.findBySharedByAndFilters(userId, normalizeResourceType(resourceType), status, pageable);
    }

    @Override
    public Page<FileShare> findSharesWithMe(UUID userId, UUID tenantId, String resourceType, Pageable pageable) {
        return shareRepository.findSharesWithMe(
                userId,
                tenantId,
                normalizeResourceType(resourceType),
                ShareStatus.ACTIVE,
                Instant.now(),
                pageable
        );
    }

    @Override
    public List<FileShare> findActiveSharesByUserScope(UUID userId, String scope) {
        String normalizedScope = normalizeScope(scope);
        Instant now = Instant.now();
        if (SCOPE_GRANTED.equals(normalizedScope)) {
            return shareRepository.findBySharedByAndStatusAndExpiresAtAfter(userId, ShareStatus.ACTIVE, now);
        }
        if (SCOPE_RECEIVED.equals(normalizedScope)) {
            return shareRepository.findBySharedWithUserIdAndStatusAndExpiresAtAfter(userId, ShareStatus.ACTIVE, now);
        }
        if (SCOPE_BOTH.equals(normalizedScope)) {
            return mergeById(
                    shareRepository.findBySharedByAndStatusAndExpiresAtAfter(userId, ShareStatus.ACTIVE, now),
                    shareRepository.findBySharedWithUserIdAndStatusAndExpiresAtAfter(userId, ShareStatus.ACTIVE, now)
            );
        }
        throw new IllegalArgumentException("Unsupported share revoke scope: " + scope);
    }

    private String normalizeResourceType(String resourceType) {
        if (resourceType == null || resourceType.isBlank()) {
            return null;
        }
        return resourceType.trim().toLowerCase(Locale.ROOT);
    }

    private String normalizeScope(String scope) {
        if (scope == null || scope.isBlank()) {
            return SCOPE_BOTH;
        }
        return scope.trim().toUpperCase(Locale.ROOT);
    }

    private List<FileShare> mergeById(List<FileShare> first, List<FileShare> second) {
        LinkedHashMap<UUID, FileShare> shares = new LinkedHashMap<>();
        first.forEach(share -> shares.put(share.getId(), share));
        second.forEach(share -> shares.putIfAbsent(share.getId(), share));
        return List.copyOf(shares.values());
    }
}
