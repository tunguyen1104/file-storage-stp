package com.filestorage.application.usecase.folder;

import com.filestorage.adapter.out.persistence.entity.Folder;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;

import java.util.UUID;

final class FolderAccessPolicy {

    private FolderAccessPolicy() {
    }

    static void requireCanRead(Folder folder, UUID actorUserId, UUID actorTenantId, RoleCode actorRole) {
        if (actorRole == RoleCode.SUPER_ADMIN || actorRole == RoleCode.TENANT_ADMIN) {
            return;
        }
        if (!folder.getTenant().getId().equals(actorTenantId)) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }
        if (!folder.getCreatedBy().getId().equals(actorUserId)) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }
    }

    static void requireCanModify(Folder folder, UUID actorUserId, UUID actorTenantId, RoleCode actorRole) {
        requireCanRead(folder, actorUserId, actorTenantId, actorRole);
    }
}

