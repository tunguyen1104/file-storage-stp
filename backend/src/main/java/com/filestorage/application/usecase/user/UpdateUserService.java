package com.filestorage.application.usecase.user;

import com.filestorage.adapter.out.persistence.entity.Role;
import com.filestorage.adapter.out.persistence.entity.User;
import com.filestorage.adapter.out.persistence.mapper.UserViewMapper;
import com.filestorage.adapter.out.persistence.repository.RoleJpaRepository;
import com.filestorage.adapter.out.persistence.repository.UserJpaRepository;
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
    public com.filestorage.domain.model.user.UserView execute(UpdateUserCommand command) {

        User target = userRepository.findByIdWithRoleAndTenant(command.targetUserId())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));
        UserWritePolicy.requireSameTenantForTenantAdmin(target, command.actorTenantId(), command.actorRole());

        if (command.fullName() == null && command.email() == null
                && command.roleCode() == null && command.storageQuotaBytes() == null) {
            throw new AppException(ErrorCode.INVALID_PAYLOAD);
        }

        if (command.fullName() != null) {
            target.setFullName(command.fullName().trim());
        }

        if (command.email() != null) {
            String normalized = command.email().trim().toLowerCase();
            if (userRepository.existsByEmailAndIdNot(normalized, target.getId())) {
                throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS);
            }
            target.setEmail(normalized);
        }

        if (command.roleCode() != null) {
            Role newRole = roleRepository.findByCode(command.roleCode())
                    .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_FOUND));
            UserWritePolicy.requireRoleAssignmentAllowed(newRole, command.actorRole());
            target.setRole(newRole);
        }

        if (command.storageQuotaBytes() != null) {
            long used = target.getStorageUsedBytes() == null ? 0L : target.getStorageUsedBytes();
            if (command.storageQuotaBytes() < used) {
                throw new AppException(ErrorCode.QUOTA_BELOW_USED);
            }
            target.setStorageQuotaBytes(command.storageQuotaBytes());
        }

        return userViewMapper.toUserView(userRepository.save(target));
    }
}

