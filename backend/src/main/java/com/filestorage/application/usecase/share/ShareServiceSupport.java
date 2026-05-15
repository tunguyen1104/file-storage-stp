package com.filestorage.application.usecase.share;

import com.filestorage.adapter.out.persistence.entity.File;
import com.filestorage.adapter.out.persistence.entity.FileShare;
import com.filestorage.adapter.out.persistence.entity.Folder;
import com.filestorage.adapter.out.persistence.entity.Tenant;
import com.filestorage.adapter.out.persistence.entity.User;
import com.filestorage.adapter.out.persistence.repository.FileJpaRepository;
import com.filestorage.adapter.out.persistence.repository.FolderJpaRepository;
import com.filestorage.adapter.out.persistence.repository.TenantJpaRepository;
import com.filestorage.adapter.out.persistence.repository.UserJpaRepository;
import com.filestorage.domain.model.enums.FileStatus;
import com.filestorage.domain.model.enums.ShareRecipientType;
import com.filestorage.domain.model.enums.ShareStatus;
import com.filestorage.domain.model.share.PaginationView;
import com.filestorage.domain.model.share.ShareExpirationView;
import com.filestorage.domain.model.share.ShareResourceView;
import com.filestorage.domain.model.share.ShareTenantView;
import com.filestorage.domain.model.share.ShareUserView;
import com.filestorage.domain.model.share.ShareView;
import com.filestorage.domain.model.share.ShareWithResourceView;
import com.filestorage.domain.model.share.SharedByView;
import com.filestorage.domain.model.share.SharedWithMeView;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class ShareServiceSupport {

    private static final String RESOURCE_FILE = "file";
    private static final String RESOURCE_FOLDER = "folder";

    private final UserJpaRepository userRepository;
    private final TenantJpaRepository tenantRepository;
    private final FileJpaRepository fileRepository;
    private final FolderJpaRepository folderRepository;

    User resolveActor(UUID actorId, UUID actorTenantId) {
        User actor = userRepository.findByIdWithRoleAndTenant(actorId)
                .orElseThrow(() -> new AppException(ErrorCode.ACCESS_DENIED));
        if (actor.getTenant() == null || !actor.getTenant().getId().equals(actorTenantId)) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
        return actor;
    }

    User resolveActor(UUID actorId) {
        return userRepository.findByIdWithRoleAndTenant(actorId)
                .orElseThrow(() -> new AppException(ErrorCode.ACCESS_DENIED));
    }

    User resolveTargetUserByEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new AppException(ErrorCode.INVALID_PAYLOAD);
        }
        return userRepository.findByEmailWithRoleAndTenant(email.trim().toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    User resolveTargetUserById(UUID userId) {
        return userRepository.findByIdWithRoleAndTenant(userId)
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
    }

    File findShareableFile(UUID fileId, UUID tenantId) {
        File file = fileRepository.findByIdAndTenantId(fileId, tenantId)
                .orElseThrow(() -> new AppException(ErrorCode.FILE_NOT_FOUND));
        if (file.getStatus() == FileStatus.DELETED) {
            throw new AppException(ErrorCode.FILE_NOT_FOUND);
        }
        return file;
    }

    Folder findShareableFolder(UUID folderId, UUID tenantId) {
        return folderRepository.findByIdAndTenantId(folderId, tenantId)
                .orElseThrow(() -> new AppException(ErrorCode.FOLDER_NOT_FOUND));
    }

    File findShareFile(FileShare share) {
        if (share.getFileId() == null) {
            throw new AppException(ErrorCode.FILE_NOT_FOUND);
        }
        return fileRepository.findById(share.getFileId())
                .orElseThrow(() -> new AppException(ErrorCode.FILE_NOT_FOUND));
    }

    Folder findShareFolder(FileShare share) {
        if (share.getFolderId() == null) {
            throw new AppException(ErrorCode.FOLDER_NOT_FOUND);
        }
        return folderRepository.findById(share.getFolderId())
                .orElseThrow(() -> new AppException(ErrorCode.FOLDER_NOT_FOUND));
    }

    UUID fileOwnerId(File file) {
        return file.getUploadedBy() == null ? null : file.getUploadedBy().getId();
    }

    UUID folderOwnerId(Folder folder) {
        return folder.getOwner() == null ? null : folder.getOwner().getId();
    }

    String normalizeResourceType(String resourceType) {
        if (resourceType == null || resourceType.isBlank()) {
            return null;
        }
        String normalized = resourceType.trim().toLowerCase(Locale.ROOT);
        if (!RESOURCE_FILE.equals(normalized) && !RESOURCE_FOLDER.equals(normalized)) {
            throw new AppException(ErrorCode.INVALID_PAYLOAD);
        }
        return normalized;
    }

    int normalizePage(int page) {
        return Math.max(1, page);
    }

    int normalizeLimit(int limit) {
        return Math.min(100, Math.max(1, limit));
    }

    PaginationView toPagination(Page<?> page) {
        return new PaginationView(
                page.getNumber() + 1,
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }

    ShareView toShareView(FileShare share) {
        return new ShareView(
                share.getId(),
                share.getTenantId(),
                resourceType(share),
                share.getFileId(),
                share.getFolderId(),
                share.getPermission(),
                share.getStatus(),
                recipientType(share),
                shareUserView(share.getSharedWithUserId()),
                shareTenantView(share.getSharedWithTenantId()),
                sharedByView(share.getSharedBy()),
                share.getExpiresAt(),
                share.getCreatedAt(),
                share.getRevokedAt()
        );
    }

    ShareWithResourceView toShareWithResourceView(FileShare share) {
        return new ShareWithResourceView(
                share.getId(),
                resourceType(share),
                share.getFileId(),
                share.getFolderId(),
                share.getPermission(),
                share.getStatus(),
                recipientType(share),
                resourceView(share),
                shareUserView(share.getSharedWithUserId()),
                shareTenantView(share.getSharedWithTenantId()),
                share.getExpiresAt(),
                share.getCreatedAt(),
                share.getRevokedAt()
        );
    }

    SharedWithMeView toSharedWithMeView(FileShare share) {
        return new SharedWithMeView(
                share.getId(),
                resourceType(share),
                share.getPermission(),
                share.getStatus(),
                recipientType(share),
                share.getExpiresAt(),
                sharedByView(share.getSharedBy()),
                resourceView(share)
        );
    }

    ShareExpirationView toShareExpirationView(FileShare share) {
        return new ShareExpirationView(
                share.getId(),
                share.getStatus(),
                share.getExpiresAt(),
                share.getRevokedAt(),
                share.getUpdatedAt()
        );
    }

    String resourceType(FileShare share) {
        return share.getFileId() != null ? RESOURCE_FILE : RESOURCE_FOLDER;
    }

    private ShareRecipientType recipientType(FileShare share) {
        return share.getSharedWithTenantId() != null ? ShareRecipientType.TENANT : ShareRecipientType.USER;
    }

    private ShareUserView shareUserView(UUID userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findById(userId)
                .map(user -> new ShareUserView(user.getId(), user.getEmail(), user.getFullName()))
                .orElse(new ShareUserView(userId, null, null));
    }

    private ShareTenantView shareTenantView(UUID tenantId) {
        if (tenantId == null) {
            return null;
        }
        return tenantRepository.findById(tenantId)
                .map(tenant -> new ShareTenantView(tenant.getId(), tenant.getName()))
                .orElse(new ShareTenantView(tenantId, null));
    }

    private SharedByView sharedByView(UUID userId) {
        if (userId == null) {
            return null;
        }
        return userRepository.findById(userId)
                .map(user -> new SharedByView(user.getId(), user.getFullName()))
                .orElse(new SharedByView(userId, null));
    }

    private ShareResourceView resourceView(FileShare share) {
        if (share.getFileId() != null) {
            return fileRepository.findById(share.getFileId())
                    .map(this::fileResourceView)
                    .orElse(new ShareResourceView(RESOURCE_FILE, share.getFileId(), null, null, null));
        }
        return folderRepository.findById(share.getFolderId())
                .map(this::folderResourceView)
                .orElse(new ShareResourceView(RESOURCE_FOLDER, share.getFolderId(), null, null, null));
    }

    private ShareResourceView fileResourceView(File file) {
        return new ShareResourceView(
                RESOURCE_FILE,
                file.getId(),
                file.getOriginalName(),
                file.getMimeType(),
                file.getSizeBytes()
        );
    }

    private ShareResourceView folderResourceView(Folder folder) {
        return new ShareResourceView(
                RESOURCE_FOLDER,
                folder.getId(),
                folder.getName(),
                null,
                null
        );
    }

    FileShare newShare(UUID tenantId, UUID sharedBy, java.time.Instant expiresAt) {
        FileShare share = new FileShare();
        share.setTenantId(tenantId);
        share.setSharedBy(sharedBy);
        share.setExpiresAt(expiresAt);
        share.setStatus(ShareStatus.ACTIVE);
        share.setUpdatedAt(java.time.Instant.now());
        return share;
    }

    UUID tenantId(User user) {
        Tenant tenant = user.getTenant();
        return tenant == null ? null : tenant.getId();
    }
}
