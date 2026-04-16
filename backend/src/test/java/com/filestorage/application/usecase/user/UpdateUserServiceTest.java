package com.filestorage.application.usecase.user;

import com.filestorage.adapter.out.persistence.entity.Role;
import com.filestorage.adapter.out.persistence.entity.Tenant;
import com.filestorage.adapter.out.persistence.entity.User;
import com.filestorage.adapter.out.persistence.mapper.UserViewMapper;
import com.filestorage.adapter.out.persistence.repository.RoleJpaRepository;
import com.filestorage.adapter.out.persistence.repository.UserJpaRepository;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.port.in.user.UpdateUserUseCase;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateUserServiceTest {

    @Mock
    private UserJpaRepository userRepository;
    @Mock
    private RoleJpaRepository roleRepository;

    private UpdateUserService updateUserService;

    @BeforeEach
    void setUp() {
        UserViewMapper mapper = Mappers.getMapper(UserViewMapper.class);
        updateUserService = new UpdateUserService(userRepository, roleRepository, mapper);
    }

    @Test
    void tenantAdminCannotUpdateCrossTenantUser() {
        User target = userEntity(UUID.randomUUID(), UUID.randomUUID(), RoleCode.MEMBER, 100L, 20L);
        when(userRepository.findByIdWithRoleAndTenant(target.getId())).thenReturn(Optional.of(target));

        var command = new UpdateUserUseCase.UpdateUserCommand(
                UUID.randomUUID(), UUID.randomUUID(), RoleCode.TENANT_ADMIN,
                target.getId(), "Name", null, null, null
        );

        assertThrows(AppException.class, () -> updateUserService.execute(command));
    }

    @Test
    void quotaCannotBeLowerThanUsed() {
        UUID tenantId = UUID.randomUUID();
        User target = userEntity(UUID.randomUUID(), tenantId, RoleCode.MEMBER, 100L, 70L);
        when(userRepository.findByIdWithRoleAndTenant(target.getId())).thenReturn(Optional.of(target));

        var command = new UpdateUserUseCase.UpdateUserCommand(
                UUID.randomUUID(), tenantId, RoleCode.TENANT_ADMIN,
                target.getId(), null, null, null, 50L
        );

        assertThrows(AppException.class, () -> updateUserService.execute(command));
    }

    @Test
    void updateSuccess() {
        UUID tenantId = UUID.randomUUID();
        User target = userEntity(UUID.randomUUID(), tenantId, RoleCode.MEMBER, 100L, 20L);
        when(userRepository.findByIdWithRoleAndTenant(target.getId())).thenReturn(Optional.of(target));
        when(userRepository.existsByEmailAndIdNot("new@example.com", target.getId())).thenReturn(false);
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var command = new UpdateUserUseCase.UpdateUserCommand(
                UUID.randomUUID(), tenantId, RoleCode.TENANT_ADMIN,
                target.getId(), "New Name", "new@example.com", null, 200L
        );

        var result = updateUserService.execute(command);
        assertEquals("new@example.com", result.email());
        assertEquals(200L, result.storageQuotaBytes());
    }

    private User userEntity(UUID userId, UUID tenantId, RoleCode roleCode, long quota, long used) {
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
                .storageQuotaBytes(quota)
                .storageUsedBytes(used)
                .build();
        user.setId(userId);
        return user;
    }
}

