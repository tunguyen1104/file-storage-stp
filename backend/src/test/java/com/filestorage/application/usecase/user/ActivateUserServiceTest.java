package com.filestorage.application.usecase.user;

import com.filestorage.adapter.out.persistence.entity.Role;
import com.filestorage.adapter.out.persistence.entity.Tenant;
import com.filestorage.adapter.out.persistence.entity.User;
import com.filestorage.adapter.out.persistence.mapper.UserViewMapper;
import com.filestorage.adapter.out.persistence.repository.TenantJpaRepository;
import com.filestorage.adapter.out.persistence.repository.UserJpaRepository;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.port.in.user.ActivateUserUseCase;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivateUserServiceTest {

    @Mock
    private UserJpaRepository userRepository;
    @Mock
    private TenantJpaRepository tenantRepository;

    private ActivateUserService activateUserService;

    @BeforeEach
    void setUp() {
        UserViewMapper mapper = Mappers.getMapper(UserViewMapper.class);
        activateUserService = new ActivateUserService(userRepository, tenantRepository, mapper);
    }

    @Test
    void activateInactiveUserReservesRemainingQuota() {
        UUID tenantId = UUID.randomUUID();
        User target = userEntity(UUID.randomUUID(), tenantId, false, 100L, 40L);
        target.getTenant().setStorageUsedBytes(200L);
        target.getTenant().setStorageQuotaBytes(1_000L);
        when(userRepository.findByIdForUpdate(target.getId())).thenReturn(Optional.of(target));
        when(tenantRepository.findByIdForUpdate(tenantId)).thenReturn(Optional.of(target.getTenant()));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var command = new ActivateUserUseCase.ActivateUserCommand(
                UUID.randomUUID(), tenantId, RoleCode.TENANT_ADMIN, target.getId()
        );

        var result = activateUserService.execute(command);
        assertTrue(result.isActive());
        assertEquals(260L, target.getTenant().getStorageUsedBytes());
    }

    @Test
    void activateRejectsWhenTenantRemainingNotEnough() {
        UUID tenantId = UUID.randomUUID();
        User target = userEntity(UUID.randomUUID(), tenantId, false, 200L, 10L);
        target.getTenant().setStorageUsedBytes(280L);
        target.getTenant().setStorageQuotaBytes(300L);
        when(userRepository.findByIdForUpdate(target.getId())).thenReturn(Optional.of(target));
        when(tenantRepository.findByIdForUpdate(tenantId)).thenReturn(Optional.of(target.getTenant()));

        var command = new ActivateUserUseCase.ActivateUserCommand(
                UUID.randomUUID(), tenantId, RoleCode.TENANT_ADMIN, target.getId()
        );

        AppException ex = assertThrows(AppException.class, () -> activateUserService.execute(command));
        assertEquals(ErrorCode.TENANT_QUOTA_EXCEEDED, ex.getError());
    }

    @Test
    void activateAlreadyActiveUserIsIdempotent() {
        UUID tenantId = UUID.randomUUID();
        User target = userEntity(UUID.randomUUID(), tenantId, true, 100L, 10L);
        target.getTenant().setStorageUsedBytes(123L);
        when(userRepository.findByIdForUpdate(target.getId())).thenReturn(Optional.of(target));

        var command = new ActivateUserUseCase.ActivateUserCommand(
                UUID.randomUUID(), tenantId, RoleCode.TENANT_ADMIN, target.getId()
        );

        var result = activateUserService.execute(command);
        assertTrue(result.isActive());
        assertEquals(123L, target.getTenant().getStorageUsedBytes());
    }

    private User userEntity(UUID userId, UUID tenantId, boolean isActive, long quota, long used) {
        Tenant tenant = Tenant.builder()
                .name("Tenant")
                .slug("tenant")
                .isActive(true)
                .storageQuotaBytes(1_000L)
                .storageUsedBytes(0L)
                .build();
        tenant.setId(tenantId);
        Role role = Role.builder().code(RoleCode.MEMBER).name("Member").build();
        role.setId(UUID.randomUUID());
        User user = User.builder()
                .tenant(tenant)
                .email("u@example.com")
                .passwordHash("hash")
                .fullName("User")
                .role(role)
                .isActive(isActive)
                .storageQuotaBytes(quota)
                .storageUsedBytes(used)
                .build();
        user.setId(userId);
        return user;
    }
}
