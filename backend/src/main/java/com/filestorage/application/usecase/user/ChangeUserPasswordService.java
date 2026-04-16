package com.filestorage.application.usecase.user;

import com.filestorage.adapter.out.persistence.entity.User;
import com.filestorage.adapter.out.persistence.repository.RefreshTokenJpaRepository;
import com.filestorage.adapter.out.persistence.repository.UserJpaRepository;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.port.in.user.ChangeUserPasswordUseCase;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class ChangeUserPasswordService implements ChangeUserPasswordUseCase {

    private final UserJpaRepository userRepository;
    private final RefreshTokenJpaRepository refreshTokenRepository;
    private final PasswordEncoder passwordEncoder;

    @Override
    @Transactional
    public void execute(ChangeUserPasswordCommand command) {
        if (!command.newPassword().equals(command.confirmPassword())) {
            throw new AppException(ErrorCode.PASSWORD_MISMATCH);
        }

        User target = userRepository.findByIdWithRoleAndTenant(command.targetUserId())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        boolean selfChange = command.actorUserId().equals(target.getId());

        if (command.actorRole() == RoleCode.MEMBER) {
            if (!selfChange || !target.getTenant().getId().equals(command.actorTenantId())) {
                throw new AppException(ErrorCode.FORBIDDEN);
            }
            requireValidCurrentPassword(command.currentPassword(), target.getPasswordHash());
        } else if (command.actorRole() == RoleCode.TENANT_ADMIN) {
            if (!target.getTenant().getId().equals(command.actorTenantId())) {
                throw new AppException(ErrorCode.FORBIDDEN);
            }
            // Admin reset user khác: không cần currentPassword. Self-change: cần currentPassword.
            if (selfChange) {
                requireValidCurrentPassword(command.currentPassword(), target.getPasswordHash());
            }
        } else if (command.actorRole() == RoleCode.SUPER_ADMIN) {
            // Super admin reset user khác: không cần currentPassword. Self-change: cần currentPassword.
            if (selfChange) {
                requireValidCurrentPassword(command.currentPassword(), target.getPasswordHash());
            }
        } else {
            throw new AppException(ErrorCode.FORBIDDEN);
        }

        target.setPasswordHash(passwordEncoder.encode(command.newPassword()));
        target.setPasswordChangedAt(Instant.now());
        userRepository.save(target);
        refreshTokenRepository.deleteByUserId(target.getId());
    }

    private void requireValidCurrentPassword(String currentPassword, String storedHash) {
        if (currentPassword == null || !passwordEncoder.matches(currentPassword, storedHash)) {
            throw new AppException(ErrorCode.INVALID_CURRENT_PASSWORD);
        }
    }
}
