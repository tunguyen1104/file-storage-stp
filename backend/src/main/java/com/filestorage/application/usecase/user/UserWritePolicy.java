package com.filestorage.application.usecase.user;

import com.filestorage.adapter.out.persistence.entity.Role;
import com.filestorage.adapter.out.persistence.entity.User;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;

import java.util.UUID;

final class UserWritePolicy {

    private UserWritePolicy() {
    }


    static void requireSameTenantForTenantAdmin(User target, UUID actorTenantId, RoleCode actorRole) {
        if (actorRole == RoleCode.TENANT_ADMIN && !target.getTenant().getId().equals(actorTenantId)) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }
    }

    static void requireNotSelfDeactivation(UUID actorUserId, UUID targetUserId, RoleCode actorRole) {
        if (actorRole == RoleCode.TENANT_ADMIN && actorUserId.equals(targetUserId)) {
            throw new AppException(ErrorCode.SELF_DEACTIVATION_FORBIDDEN);
        }
    }

    static void requireRoleAssignmentAllowed(Role role, RoleCode actorRole) {
        if (actorRole == RoleCode.TENANT_ADMIN && role.getCode() == RoleCode.SUPER_ADMIN) {
            throw new AppException(ErrorCode.ROLE_ESCALATION_FORBIDDEN);
        }
    }

    static void requireTenantAdminRoleDemotionAllowed(UUID actorUserId, User target, Role newRole, RoleCode actorRole) {
        if (actorRole != RoleCode.TENANT_ADMIN) {
            return;
        }
        if (target.getRole().getCode() == RoleCode.TENANT_ADMIN && newRole.getCode() != RoleCode.TENANT_ADMIN) {
            throw new AppException(ErrorCode.TENANT_ADMIN_ROLE_DEMOTION_FORBIDDEN);
        }
        if (actorUserId.equals(target.getId()) && target.getRole().getCode() != newRole.getCode()) {
            throw new AppException(ErrorCode.TENANT_ADMIN_ROLE_DEMOTION_FORBIDDEN);
        }
    }

    static UUID resolveTenantForCreate(RoleCode actorRole, UUID actorTenantId, UUID requestedTenantId) {
        if (actorRole == RoleCode.SUPER_ADMIN) {
            return requestedTenantId == null ? actorTenantId : requestedTenantId;
        }
        return actorTenantId;
    }
}
