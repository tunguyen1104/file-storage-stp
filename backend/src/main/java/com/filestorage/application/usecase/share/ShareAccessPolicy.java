package com.filestorage.application.usecase.share;

import com.filestorage.adapter.out.persistence.entity.FileShare;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.enums.ShareStatus;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;

import java.time.Instant;
import java.util.UUID;

public final class ShareAccessPolicy {

    private ShareAccessPolicy() {
    }

    public static void validateExpiresAt(Instant expiresAt) {
        if (expiresAt == null) {
            throw new AppException(ErrorCode.EXPIRES_AT_REQUIRED);
        }
        if (!expiresAt.isAfter(Instant.now())) {
            throw new AppException(ErrorCode.EXPIRES_AT_IN_PAST);
        }
    }

    public static void validateOwnership(UUID ownerId, UUID actorId) {
        if (ownerId == null || actorId == null || !ownerId.equals(actorId)) {
            throw new AppException(ErrorCode.FORBIDDEN_NOT_RESOURCE_OWNER);
        }
    }

    public static void validateNotSelf(UUID ownerId, UUID targetUserId) {
        if (ownerId != null && ownerId.equals(targetUserId)) {
            throw new AppException(ErrorCode.SELF_SHARE_NOT_ALLOWED);
        }
    }

    public static void validateSameTenant(UUID actorTenantId, UUID targetUserTenantId) {
        if (actorTenantId == null || targetUserTenantId == null || !actorTenantId.equals(targetUserTenantId)) {
            throw new AppException(ErrorCode.CROSS_TENANT_SHARE_NOT_ALLOWED);
        }
    }

    public static void validateTenantAdminTargetTenant(RoleCode role, UUID actorTenantId, UUID targetTenantId) {
        if (role == RoleCode.TENANT_ADMIN
                && targetTenantId != null
                && !targetTenantId.equals(actorTenantId)) {
            throw new AppException(ErrorCode.CROSS_TENANT_SHARE_NOT_ALLOWED);
        }
    }

    public static void validateNotRevoked(FileShare share) {
        if (share != null && share.getStatus() == ShareStatus.REVOKED) {
            throw new AppException(ErrorCode.SHARE_REVOKED_RESHARE_REQUIRED);
        }
    }
}
