package com.filestorage.application.usecase.user;

import com.filestorage.adapter.out.persistence.entity.Role;
import com.filestorage.adapter.out.persistence.entity.Tenant;
import com.filestorage.adapter.out.persistence.entity.User;
import com.filestorage.adapter.out.persistence.mapper.UserViewMapper;
import com.filestorage.adapter.out.persistence.repository.RoleJpaRepository;
import com.filestorage.adapter.out.persistence.repository.TenantJpaRepository;
import com.filestorage.adapter.out.persistence.repository.UserJpaRepository;
import com.filestorage.domain.model.audit.AuditAction;
import com.filestorage.domain.model.user.UserView;
import com.filestorage.domain.port.in.audit.LogAuditEventUseCase;
import com.filestorage.domain.port.in.audit.LogAuditEventUseCase.AuditEvent;
import com.filestorage.domain.port.in.user.UpdateUserUseCase;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import com.filestorage.shared.util.RequestContext;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class UpdateUserService implements UpdateUserUseCase {

    private final UserJpaRepository userRepository;
    private final RoleJpaRepository roleRepository;
    private final TenantJpaRepository tenantRepository;
    private final UserViewMapper userViewMapper;
    private final LogAuditEventUseCase auditLogger;

    @Override
    @Transactional
    public UserView execute(UpdateUserCommand command) {

        User target = userRepository.findByIdForUpdate(command.targetUserId())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        UserWritePolicy.requireSameTenantForTenantAdmin(target, command.actorTenantId(), command.actorRole());

        target.setFullName(command.fullName().trim());

        String oldRoleCode = target.getRole().getCode().name();
        Role newRole = roleRepository.findByCode(command.roleCode())
                    .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_FOUND));
        UserWritePolicy.requireRoleAssignmentAllowed(newRole, command.actorRole());
        UserWritePolicy.requireTenantAdminRoleDemotionAllowed(command.actorUserId(), target, newRole, command.actorRole());
        target.setRole(newRole);
        boolean roleChanged = !oldRoleCode.equals(newRole.getCode().name());

        if (command.storageQuotaBytes() != null) {
            if (command.storageQuotaBytes() < target.getStorageUsedBytes()) {
                throw new AppException(ErrorCode.QUOTA_BELOW_USED);
            }

            if (target.isActive()) {
                Tenant tenant = tenantRepository.findByIdForUpdate(target.getTenant().getId())
                        .orElseThrow(() -> new AppException(ErrorCode.TENANT_NOT_FOUND));
                target.setTenant(tenant);

                long tenantUsed = tenant.getStorageUsedBytes();
                long tenantRemainingQuota = Math.subtractExact(tenant.getStorageQuotaBytes(), tenantUsed);
                long delta = Math.subtractExact(command.storageQuotaBytes(), target.getStorageQuotaBytes());
                if (delta > 0 && delta > tenantRemainingQuota) {
                    throw new AppException(ErrorCode.TENANT_QUOTA_EXCEEDED);
                }
                tenant.setStorageUsedBytes(Math.addExact(tenantUsed, delta));
                tenantRepository.save(tenant);
            }
            target.setStorageQuotaBytes(command.storageQuotaBytes());
        }

        UserView view = userViewMapper.toUserView(userRepository.save(target));

        if (roleChanged) {
            logRoleChange(command, target, oldRoleCode, newRole.getCode().name());
        }
        return view;
    }

    private void logRoleChange(UpdateUserCommand command, User target, String oldRoleCode, String newRoleCode) {
        HttpServletRequest request = RequestContext.getCurrentRequest().orElse(null);
        Map<String, Object> extra = new HashMap<>();
        extra.put("actor_role", command.actorRole().name());
        extra.put("actor_tenant_id", command.actorTenantId().toString());
        extra.put("target_user_id", target.getId().toString());
        extra.put("old_role", oldRoleCode);
        extra.put("new_role", newRoleCode);
        String requestId = RequestContext.getRequestId(request);
        if (requestId != null) {
            extra.put("request_id", requestId);
        }
        auditLogger.logAsync(new AuditEvent(
                AuditAction.USER_ROLE_CHANGED,
                target.getTenant().getId(),
                command.actorUserId(),
                null,
                command.actorRole().name(),
                RequestContext.getClientIp(request),
                RequestContext.getUserAgent(request),
                requestId,
                extra
        ));
    }
}
