package com.filestorage.application.usecase.folder;

import com.filestorage.adapter.out.persistence.entity.Folder;
import com.filestorage.application.usecase.share.ShareResolutionService;
import com.filestorage.domain.model.enums.MyPermission;
import com.filestorage.domain.model.enums.PermissionSource;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.share.PermissionResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class FolderAccessPolicy {

    private final ShareResolutionService shareResolution;

    public void requireCanRead(Folder folder, UUID actorUserId, UUID actorTenantId, RoleCode actorRole) {
        PermissionResult result = shareResolution.resolveFolder(actorUserId, actorTenantId, actorRole, folder);
        if (result == null) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }
    }

    public void requireCanModify(Folder folder, UUID actorUserId, UUID actorTenantId, RoleCode actorRole) {
        PermissionResult result = shareResolution.resolveFolder(actorUserId, actorTenantId, actorRole, folder);
        if (result == null || (result.permission() != MyPermission.OWNER && result.permission() != MyPermission.ADMIN)) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }
    }

    public void requireCanWriteDirect(Folder folder, UUID actorUserId, UUID actorTenantId, RoleCode actorRole) {
        PermissionResult result = shareResolution.resolveFolder(actorUserId, actorTenantId, actorRole, folder);
        if (result == null) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }
        if (result.permission() == MyPermission.OWNER || result.permission() == MyPermission.ADMIN) {
            return;
        }
        if (result.permission() == MyPermission.EDITOR && result.source() == PermissionSource.DIRECT_SHARE) {
            return;
        }
        throw new AppException(ErrorCode.FORBIDDEN);
    }
}
