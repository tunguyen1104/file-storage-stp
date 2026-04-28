package com.filestorage.application.usecase.user;

import com.filestorage.adapter.out.messaging.EmailEventPublisher;
import com.filestorage.adapter.out.messaging.event.EmailEvent;
import com.filestorage.adapter.out.persistence.entity.Role;
import com.filestorage.adapter.out.persistence.entity.Tenant;
import com.filestorage.adapter.out.persistence.entity.User;
import com.filestorage.adapter.out.persistence.mapper.UserViewMapper;
import com.filestorage.adapter.out.persistence.repository.RoleJpaRepository;
import com.filestorage.adapter.out.persistence.repository.TenantJpaRepository;
import com.filestorage.adapter.out.persistence.repository.UserJpaRepository;
import com.filestorage.domain.model.user.UserView;
import com.filestorage.domain.port.in.user.CreateUserUseCase;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class CreateUserService implements CreateUserUseCase {

    private static final long DEFAULT_USER_QUOTA_BYTES = 10_737_418_240L;

    private final UserJpaRepository userRepository;
    private final RoleJpaRepository roleRepository;
    private final TenantJpaRepository tenantRepository;
    private final UserViewMapper userViewMapper;
    private final PasswordEncoder passwordEncoder;
    private final PasswordGenerator passwordGenerator;
    private final EmailEventPublisher emailEventPublisher;

    @Value("${app.frontend.base-url:http://localhost:3000}")
    private String frontendBaseUrl;

    @Override
    @Transactional
    public UserView execute(CreateUserCommand command) {

        String normalizedEmail = normalizeEmail(command.email());
        if (userRepository.existsByEmail(normalizedEmail)) {
            throw new AppException(ErrorCode.EMAIL_ALREADY_EXISTS);
        }

        Role role = roleRepository.findByCode(command.roleCode())
                .orElseThrow(() -> new AppException(ErrorCode.ROLE_NOT_FOUND));
        UserWritePolicy.requireRoleAssignmentAllowed(role, command.actorRole());

        Tenant tenant = tenantRepository.findById(
                        UserWritePolicy.resolveTenantForCreate(command.actorRole(), command.actorTenantId(), command.tenantId()))
                .orElseThrow(() -> new AppException(ErrorCode.TENANT_NOT_FOUND));

        User actor = userRepository.findById(command.actorUserId())
                .orElseThrow(() -> new AppException(ErrorCode.USER_NOT_FOUND));

        long requestedQuota = getQuota(command, tenant);

        String rawPassword = passwordGenerator.generate();
        User created = User.builder()
                .tenant(tenant)
                .email(normalizedEmail)
                .passwordHash(passwordEncoder.encode(rawPassword))
                .fullName(command.fullName().trim())
                .role(role)
                .isActive(true)
                .storageQuotaBytes(requestedQuota)
                .storageUsedBytes(0L)
                .createdBy(actor)
                .build();

        User saved = userRepository.save(created);
        publishInitialPasswordEmail(saved, rawPassword);
        return userViewMapper.toUserView(saved);
    }

    private static long getQuota(CreateUserCommand command, Tenant tenant) {
        long requestedQuota = command.storageQuotaBytes() == null ? DEFAULT_USER_QUOTA_BYTES : command.storageQuotaBytes();
        long tenantQuota = tenant.getStorageQuotaBytes() == null ? Long.MAX_VALUE : tenant.getStorageQuotaBytes();
        long tenantUsed = tenant.getStorageUsedBytes() == null ? 0L : tenant.getStorageUsedBytes();
        long tenantRemainingQuota = tenantQuota - tenantUsed;
        if (requestedQuota > tenantRemainingQuota) {
            throw new AppException(ErrorCode.TENANT_QUOTA_EXCEEDED);
        }
        return requestedQuota;
    }

    private void publishInitialPasswordEmail(User user, String rawPassword) {
        try {
            emailEventPublisher.publishInitialPassword(
                    EmailEvent.initialPassword(user.getEmail(), Map.of(
                            "fullName", user.getFullName(),
                            "temporaryPassword", rawPassword,
                            "loginUrl", frontendBaseUrl + "/login"
                    ))
            );
        } catch (Exception ex) {
            log.error("Failed to publish INITIAL_PASSWORD email event for user {}: {}", user.getId(), ex.getMessage());
        }
    }

    private String normalizeEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new AppException(ErrorCode.INVALID_PAYLOAD);
        }
        return email.trim().toLowerCase();
    }
}
