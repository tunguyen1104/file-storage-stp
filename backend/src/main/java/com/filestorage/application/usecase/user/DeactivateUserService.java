package com.filestorage.application.usecase.user;

import com.filestorage.adapter.out.persistence.entity.User;
import com.filestorage.adapter.out.persistence.entity.Tenant;
import com.filestorage.adapter.out.persistence.mapper.UserViewMapper;
import com.filestorage.adapter.out.persistence.repository.RefreshTokenJpaRepository;
import com.filestorage.adapter.out.persistence.repository.TenantJpaRepository;
import com.filestorage.adapter.out.persistence.repository.UserJpaRepository;
import com.filestorage.domain.model.audit.AuditAction;
import com.filestorage.domain.model.user.UserView;
import com.filestorage.domain.port.in.user.DeactivateUserUseCase;
import com.filestorage.infrastructure.audit.Auditable;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeactivateUserService implements DeactivateUserUseCase {

    private final UserJpaRepository userRepository;
    private final RefreshTokenJpaRepository refreshTokenRepository;
    private final TenantJpaRepository tenantRepository;
    private final UserViewMapper userViewMapper;

    @Override
    @Transactional
    @Auditable(
            action = AuditAction.USER_DEACTIVATED,
            tenantIdSpel = "#result.tenantId()",
            extraSpel = "{'target_user_id': #command.targetUserId().toString()}"
    )
    public UserView execute(DeactivateUserCommand command) {

        User target = userRepository.findByIdForUpdate(command.targetUserId())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        UserWritePolicy.requireSameTenantForTenantAdmin(target, command.actorTenantId(), command.actorRole());
        UserWritePolicy.requireNotSelfDeactivation(command.actorUserId(), target.getId(), command.actorRole());

        if (target.isActive()) {
            Tenant tenant = tenantRepository.findByIdForUpdate(target.getTenant().getId())
                    .orElseThrow(() -> new AppException(ErrorCode.TENANT_NOT_FOUND));

            long quota = target.getStorageQuotaBytes();
            long used = target.getStorageUsedBytes();
            long unusedQuota = Math.subtractExact(quota, used);
            tenant.setStorageUsedBytes(Math.subtractExact(tenant.getStorageUsedBytes(), unusedQuota));
            target.setActive(false);
            tenantRepository.save(tenant);
            userRepository.save(target);
        }
        refreshTokenRepository.deleteByUserId(target.getId());
        return userViewMapper.toUserView(target);
    }
}
