package com.filestorage.application.usecase.user;

import com.filestorage.adapter.out.persistence.entity.Role;
import com.filestorage.adapter.out.persistence.entity.Tenant;
import com.filestorage.adapter.out.persistence.entity.User;
import com.filestorage.adapter.out.persistence.repository.RefreshTokenJpaRepository;
import com.filestorage.adapter.out.persistence.repository.UserJpaRepository;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.port.in.user.ChangeUserPasswordUseCase;
import com.filestorage.shared.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChangeUserPasswordServiceTest {

    @Mock
    private UserJpaRepository userRepository;
    @Mock
    private RefreshTokenJpaRepository refreshTokenRepository;
    @Mock
    private PasswordEncoder passwordEncoder;

    private ChangeUserPasswordService changeUserPasswordService;

    @BeforeEach
    void setUp() {
        changeUserPasswordService = new ChangeUserPasswordService(userRepository, refreshTokenRepository, passwordEncoder);
    }

    @Test
    void memberMustProvideCorrectCurrentPassword() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User target = userEntity(userId, tenantId, RoleCode.MEMBER);
        when(userRepository.findByIdWithRoleAndTenant(userId)).thenReturn(Optional.of(target));
        when(passwordEncoder.matches("wrong", "hash")).thenReturn(false);

        var command = new ChangeUserPasswordUseCase.ChangeUserPasswordCommand(
                userId, tenantId, RoleCode.MEMBER, userId,
                "wrong", "NewP@ss1", "NewP@ss1"
        );

        assertThrows(AppException.class, () -> changeUserPasswordService.execute(command));
    }

    @Test
    void tenantAdminCanResetWithoutCurrentPassword() {
        UUID tenantId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        User target = userEntity(targetId, tenantId, RoleCode.MEMBER);
        when(userRepository.findByIdWithRoleAndTenant(targetId)).thenReturn(Optional.of(target));
        when(passwordEncoder.encode(anyString())).thenReturn("encoded");

        var command = new ChangeUserPasswordUseCase.ChangeUserPasswordCommand(
                UUID.randomUUID(), tenantId, RoleCode.TENANT_ADMIN, targetId,
                null, "NewP@ss1", "NewP@ss1"
        );

        changeUserPasswordService.execute(command);
    }

    @Test
    void tenantAdminSelfChangeRequiresCurrentPassword() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User target = userEntity(userId, tenantId, RoleCode.TENANT_ADMIN);
        when(userRepository.findByIdWithRoleAndTenant(userId)).thenReturn(Optional.of(target));
        when(passwordEncoder.matches("bad", "hash")).thenReturn(false);

        var command = new ChangeUserPasswordUseCase.ChangeUserPasswordCommand(
                userId, tenantId, RoleCode.TENANT_ADMIN, userId,
                "bad", "NewP@ss1", "NewP@ss1"
        );

        assertThrows(AppException.class, () -> changeUserPasswordService.execute(command));
    }

    private User userEntity(UUID userId, UUID tenantId, RoleCode roleCode) {
        Tenant tenant = Tenant.builder().name("T").slug("t").isActive(true).build();
        tenant.setId(tenantId);

        Role role = Role.builder().code(roleCode).name(roleCode.name()).build();
        role.setId(UUID.randomUUID());

        User user = User.builder()
                .tenant(tenant)
                .email("u@example.com")
                .passwordHash("hash")
                .fullName("User")
                .role(role)
                .isActive(true)
                .storageQuotaBytes(100L)
                .storageUsedBytes(10L)
                .build();
        user.setId(userId);
        return user;
    }
}
