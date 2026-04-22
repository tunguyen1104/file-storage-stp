package com.filestorage.application.usecase.file;

import com.filestorage.adapter.out.persistence.entity.File;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;

import java.util.UUID;

final class FileAccessPolicy {

    private FileAccessPolicy() {
    }

    static void requireCanDownload(File file, UUID actorUserId, UUID actorTenantId, RoleCode actorRole) {
        if (actorRole == RoleCode.SUPER_ADMIN || actorRole == RoleCode.TENANT_ADMIN) {
            return;
        }
        if (!file.getTenant().getId().equals(actorTenantId)) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }
        if (!file.getUploadedBy().getId().equals(actorUserId)) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }
    }

    static void requireCanModify(File file, UUID actorUserId, UUID actorTenantId, RoleCode actorRole) {
        requireCanDownload(file, actorUserId, actorTenantId, actorRole);
    }
}

