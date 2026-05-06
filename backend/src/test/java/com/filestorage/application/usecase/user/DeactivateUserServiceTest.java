package com.filestorage.application.usecase.user;

import com.filestorage.adapter.out.persistence.entity.Role;
import com.filestorage.adapter.out.persistence.entity.Tenant;
import com.filestorage.adapter.out.persistence.entity.User;
import com.filestorage.adapter.out.persistence.mapper.UserViewMapper;
import com.filestorage.adapter.out.persistence.repository.RefreshTokenJpaRepository;
import com.filestorage.adapter.out.persistence.repository.TenantJpaRepository;
import com.filestorage.adapter.out.persistence.repository.UserJpaRepository;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.port.in.user.DeactivateUserUseCase;
import com.filestorage.shared.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeactivateUserServiceTest {

    @Mock
    private UserJpaRepository userRepository;
    @Mock
    private RefreshTokenJpaRepository refreshTokenRepository;
    @Mock
    private TenantJpaRepository tenantRepository;

    private DeactivateUserService deactivateUserService;

    @BeforeEach
    void setUp() {
        UserViewMapper mapper = Mappers.getMapper(UserViewMapper.class);
        deactivateUserService = new DeactivateUserService(userRepository, refreshTokenRepository, tenantRepository, mapper);
    }

    @Test
    void tenantAdminCannotDeactivateSelf() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        User target = userEntity(userId, tenantId, RoleCode.TENANT_ADMIN);
        when(userRepository.findByIdWithRoleAndTenant(userId)).thenReturn(Optional.of(target));

        var command = new DeactivateUserUseCase.DeactivateUserCommand(userId, tenantId, RoleCode.TENANT_ADMIN, userId);
        assertThrows(AppException.class, () -> deactivateUserService.execute(command));
    }

    @Test
    void deactivateSuccess() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        User target = userEntity(userId, tenantId, RoleCode.MEMBER);
        when(userRepository.findByIdWithRoleAndTenant(userId)).thenReturn(Optional.of(target));

        var command = new DeactivateUserUseCase.DeactivateUserCommand(UUID.randomUUID(), tenantId, RoleCode.TENANT_ADMIN, userId);
        var result = deactivateUserService.execute(command);

        assertFalse(result.isActive());
        assertFalse(target.isActive());
        assertEquals(10L, target.getTenant().getStorageUsedBytes());
    }

    private User userEntity(UUID userId, UUID tenantId, RoleCode roleCode) {
        Tenant tenant = Tenant.builder().name("T").slug("t").isActive(true).build();
        tenant.setId(tenantId);
        tenant.setStorageUsedBytes(100L);

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
