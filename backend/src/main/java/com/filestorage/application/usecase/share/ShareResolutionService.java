package com.filestorage.application.usecase.share;

import com.filestorage.adapter.out.persistence.entity.File;
import com.filestorage.adapter.out.persistence.entity.FileShare;
import com.filestorage.adapter.out.persistence.entity.Folder;
import com.filestorage.domain.model.enums.MyPermission;
import com.filestorage.domain.model.enums.PermissionFile;
import com.filestorage.domain.model.enums.PermissionSource;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.share.PermissionResult;
import com.filestorage.domain.port.out.ShareRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ShareResolutionService {

    private static final PermissionResult OWNER =
            new PermissionResult(MyPermission.OWNER, PermissionSource.OWNERSHIP);
    private static final PermissionResult ADMIN =
            new PermissionResult(MyPermission.ADMIN, PermissionSource.ADMIN_PRIVILEGE);
    private static final PermissionResult DIRECT_EDITOR =
            new PermissionResult(MyPermission.EDITOR, PermissionSource.DIRECT_SHARE);
    private static final PermissionResult DIRECT_VIEWER =
            new PermissionResult(MyPermission.VIEWER, PermissionSource.DIRECT_SHARE);
    private static final PermissionResult INHERITED_VIEWER =
            new PermissionResult(MyPermission.VIEWER, PermissionSource.INHERITED);

    private final ShareRepositoryPort shareRepository;

    public PermissionResult resolveFolder(UUID actorId, UUID actorTenantId, RoleCode role, Folder folder) {
        if (folder == null || folder.getTenant() == null || folder.getTenant().getId() == null) {
            return null;
        }
        UUID resourceTenantId = folder.getTenant().getId();
        if (!canAccessTenant(actorTenantId, role, resourceTenantId)) {
            return null;
        }
        if (role == RoleCode.SUPER_ADMIN || role == RoleCode.TENANT_ADMIN) {
            return ADMIN;
        }
        if (isFolderOwner(folder, actorId)) {
            return OWNER;
        }
        if (folder.getId() == null) {
            return null;
        }

        Optional<FileShare> directUserShare = shareRepository.findActiveUserFolderShare(folder.getId(), actorId);
        if (directUserShare.isPresent()) {
            return directUserShare.get().getPermission() == PermissionFile.EDIT ? DIRECT_EDITOR : DIRECT_VIEWER;
        }
        if (shareRepository.hasActiveTenantFolderShare(folder.getId(), actorTenantId)) {
            return DIRECT_VIEWER;
        }
        if (shareRepository.hasActiveUserShareOnAnyAncestor(folder.getId(), actorId)
                || shareRepository.hasActiveTenantShareOnAnyAncestor(folder.getId(), actorTenantId)) {
            return INHERITED_VIEWER;
        }
        return null;
    }

    public PermissionResult resolveFile(UUID actorId, UUID actorTenantId, RoleCode role, File file) {
        if (file == null || file.getTenant() == null || file.getTenant().getId() == null) {
            return null;
        }
        UUID resourceTenantId = file.getTenant().getId();
        if (!canAccessTenant(actorTenantId, role, resourceTenantId)) {
            return null;
        }
        if (role == RoleCode.SUPER_ADMIN || role == RoleCode.TENANT_ADMIN) {
            return ADMIN;
        }
        if (isFileOwner(file, actorId)) {
            return OWNER;
        }
        if (file.getId() != null && shareRepository.findActiveUserFileShare(file.getId(), actorId).isPresent()) {
            return DIRECT_VIEWER;
        }
        if (file.getId() != null && shareRepository.hasActiveTenantFileShare(file.getId(), actorTenantId)) {
            return DIRECT_VIEWER;
        }
        if (file.getFolder() != null && file.getFolder().getId() != null
                && (shareRepository.hasActiveUserShareOnAnyAncestor(file.getFolder().getId(), actorId)
                || shareRepository.hasActiveTenantShareOnAnyAncestor(file.getFolder().getId(), actorTenantId))) {
            return INHERITED_VIEWER;
        }
        return null;
    }

    private boolean canAccessTenant(UUID actorTenantId, RoleCode role, UUID resourceTenantId) {
        if (role == RoleCode.SUPER_ADMIN) {
            return true;
        }
        return actorTenantId != null && actorTenantId.equals(resourceTenantId);
    }

    private boolean isFolderOwner(Folder folder, UUID actorId) {
        return actorId != null
                && folder.getOwner() != null
                && actorId.equals(folder.getOwner().getId());
    }

    private boolean isFileOwner(File file, UUID actorId) {
        return actorId != null
                && file.getUploadedBy() != null
                && actorId.equals(file.getUploadedBy().getId());
    }
}
