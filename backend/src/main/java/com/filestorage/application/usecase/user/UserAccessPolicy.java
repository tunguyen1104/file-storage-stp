package com.filestorage.application.usecase.user;

import com.filestorage.adapter.out.persistence.entity.User;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;

import java.util.UUID;

final class UserAccessPolicy {

    private UserAccessPolicy() {
    }

    static void requireCanList(RoleCode actorRole) {
        if (actorRole != RoleCode.SUPER_ADMIN) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }
    }

    static void requireCanListTenantUsers(RoleCode actorRole) {
        if (actorRole != RoleCode.SUPER_ADMIN && actorRole != RoleCode.TENANT_ADMIN) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }
    }

    static void requireCanRead(User target, UUID actorUserId, UUID actorTenantId, RoleCode actorRole) {
        if (actorRole == RoleCode.SUPER_ADMIN) {
            return;
        }

        if (!target.getTenant().getId().equals(actorTenantId)) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }

        if (actorRole == RoleCode.MEMBER && !target.getId().equals(actorUserId)) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }
    }
}

