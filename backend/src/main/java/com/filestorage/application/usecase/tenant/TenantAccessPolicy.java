package com.filestorage.application.usecase.tenant;

import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;

final class TenantAccessPolicy {

    private TenantAccessPolicy() {
    }

    static void requireSuperAdmin(RoleCode actorRole) {
        if (actorRole != RoleCode.SUPER_ADMIN) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }
    }
}

