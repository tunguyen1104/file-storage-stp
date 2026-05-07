package com.filestorage.application.usecase.user;

import com.filestorage.adapter.out.persistence.entity.Tenant;
import com.filestorage.adapter.out.persistence.entity.User;
import com.filestorage.adapter.out.persistence.mapper.UserViewMapper;
import com.filestorage.adapter.out.persistence.repository.TenantJpaRepository;
import com.filestorage.adapter.out.persistence.repository.UserJpaRepository;
import com.filestorage.domain.model.user.UserView;
import com.filestorage.domain.port.in.user.ActivateUserUseCase;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ActivateUserService implements ActivateUserUseCase {

    private final UserJpaRepository userRepository;
    private final TenantJpaRepository tenantRepository;
    private final UserViewMapper userViewMapper;

    @Override
    @Transactional
    public UserView execute(ActivateUserCommand command) {
        User target = userRepository.findByIdForUpdate(command.targetUserId())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        UserWritePolicy.requireSameTenantForTenantAdmin(target, command.actorTenantId(), command.actorRole());

        if (target.isActive()) {
            return userViewMapper.toUserView(target);
        }

        Tenant tenant = tenantRepository.findByIdForUpdate(target.getTenant().getId())
                .orElseThrow(() -> new AppException(ErrorCode.TENANT_NOT_FOUND));
        long quota = target.getStorageQuotaBytes();
        long used = target.getStorageUsedBytes();
        if (quota < used) {
            throw new AppException(ErrorCode.QUOTA_BELOW_USED);
        }

        long quotaToReserve = Math.subtractExact(quota, used);
        long tenantUsed = tenant.getStorageUsedBytes();
        long tenantRemaining = Math.subtractExact(tenant.getStorageQuotaBytes(), tenantUsed);
        if (quotaToReserve > tenantRemaining) {
            throw new AppException(ErrorCode.TENANT_QUOTA_EXCEEDED);
        }

        tenant.setStorageUsedBytes(Math.addExact(tenantUsed, quotaToReserve));
        target.setTenant(tenant);
        target.setActive(true);
        tenantRepository.save(tenant);
        return userViewMapper.toUserView(userRepository.save(target));
    }
}
