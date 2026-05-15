package com.filestorage.application.usecase.file;

import com.filestorage.adapter.out.persistence.entity.File;
import com.filestorage.application.usecase.share.ShareResolutionService;
import com.filestorage.domain.model.enums.MyPermission;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.share.PermissionResult;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;

import java.util.UUID;

@Component
@RequiredArgsConstructor
class FileAccessPolicy {

    private final ShareResolutionService shareResolution;

    void requireCanDownload(File file, UUID actorUserId, UUID actorTenantId, RoleCode actorRole) {
        PermissionResult result = shareResolution.resolveFile(actorUserId, actorTenantId, actorRole, file);
        if (result == null) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }
    }

    void requireCanModify(File file, UUID actorUserId, UUID actorTenantId, RoleCode actorRole) {
        PermissionResult result = shareResolution.resolveFile(actorUserId, actorTenantId, actorRole, file);
        if (result == null || (result.permission() != MyPermission.OWNER && result.permission() != MyPermission.ADMIN)) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }
    }
}
