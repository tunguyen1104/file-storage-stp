package com.filestorage.application.usecase.user;

import com.filestorage.adapter.out.persistence.entity.Role;
import com.filestorage.adapter.out.persistence.entity.User;
import com.filestorage.adapter.out.persistence.mapper.UserViewMapper;
import com.filestorage.adapter.out.persistence.repository.RoleJpaRepository;
import com.filestorage.adapter.out.persistence.repository.UserJpaRepository;
import com.filestorage.domain.model.user.UserView;
import com.filestorage.domain.port.in.user.UpdateUserUseCase;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdateUserService implements UpdateUserUseCase {

    private final UserJpaRepository userRepository;
    private final RoleJpaRepository roleRepository;
    private final UserViewMapper userViewMapper;

    @Override
    @Transactional
    public UserView execute(UpdateUserCommand command) {

        User target = userRepository.findByIdWithRoleAndTenant(command.targetUserId())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        UserWritePolicy.requireSameTenantForTenantAdmin(target, command.actorTenantId(), command.actorRole());

        target.setFullName(command.fullName().trim());

        Role newRole = roleRepository.findByCode(command.roleCode())
                    .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_FOUND));
        UserWritePolicy.requireRoleAssignmentAllowed(newRole, command.actorRole());
        target.setRole(newRole);


        if (command.storageQuotaBytes() != null) {
            long tenantQuota = target.getTenant().getStorageQuotaBytes() == null
                    ? Long.MAX_VALUE
                    : target.getTenant().getStorageQuotaBytes();
            long tenantUsed = target.getTenant().getStorageUsedBytes() == null ? 0L : target.getTenant().getStorageUsedBytes();
            long tenantRemainingQuota = tenantQuota - tenantUsed;
            if (command.storageQuotaBytes() > tenantRemainingQuota) {
                throw new AppException(ErrorCode.TENANT_QUOTA_EXCEEDED);
            }

            long used = target.getStorageUsedBytes() == null ? 0L : target.getStorageUsedBytes();
            if (command.storageQuotaBytes() < used) {
                throw new AppException(ErrorCode.QUOTA_BELOW_USED);
            }
            target.setStorageQuotaBytes(command.storageQuotaBytes());
        }

        return userViewMapper.toUserView(userRepository.save(target));
    }
}

