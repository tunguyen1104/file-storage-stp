package com.filestorage.application.usecase.user;

import com.filestorage.adapter.out.persistence.entity.Role;
import com.filestorage.adapter.out.persistence.entity.Tenant;
import com.filestorage.adapter.out.persistence.entity.User;
import com.filestorage.adapter.out.persistence.mapper.UserViewMapper;
import com.filestorage.adapter.out.persistence.repository.RoleJpaRepository;
import com.filestorage.adapter.out.persistence.repository.TenantJpaRepository;
import com.filestorage.adapter.out.persistence.repository.UserJpaRepository;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.port.in.audit.LogAuditEventUseCase;
import com.filestorage.domain.port.in.user.UpdateUserUseCase;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UpdateUserServiceTest {

    @Mock
    private UserJpaRepository userRepository;
    @Mock
    private RoleJpaRepository roleRepository;
    @Mock
    private TenantJpaRepository tenantRepository;
    @Mock
    private LogAuditEventUseCase auditLogger;

    private UpdateUserService updateUserService;

    @BeforeEach
    void setUp() {
        UserViewMapper mapper = Mappers.getMapper(UserViewMapper.class);
        updateUserService = new UpdateUserService(userRepository, roleRepository, tenantRepository, mapper, auditLogger);
    }

    @Test
    void tenantAdminCannotUpdateCrossTenantUser() {
        User target = userEntity(UUID.randomUUID(), UUID.randomUUID(), RoleCode.MEMBER, true, 100L, 20L);
        when(userRepository.findByIdForUpdate(target.getId())).thenReturn(Optional.of(target));

        var command = new UpdateUserUseCase.UpdateUserCommand(
                UUID.randomUUID(), UUID.randomUUID(), RoleCode.TENANT_ADMIN,
                target.getId(), "Name", RoleCode.MEMBER, null
        );

        assertThrows(AppException.class, () -> updateUserService.execute(command));
    }

    @Test
    void quotaCannotBeLowerThanUsedForInactiveUser() {
        UUID tenantId = UUID.randomUUID();
        User target = userEntity(UUID.randomUUID(), tenantId, RoleCode.MEMBER, false, 100L, 70L);
        when(userRepository.findByIdForUpdate(target.getId())).thenReturn(Optional.of(target));
        when(roleRepository.findByCode(RoleCode.MEMBER)).thenReturn(Optional.of(target.getRole()));

        var command = new UpdateUserUseCase.UpdateUserCommand(
                UUID.randomUUID(), tenantId, RoleCode.TENANT_ADMIN,
                target.getId(), "User", RoleCode.MEMBER, 50L
        );

        AppException ex = assertThrows(AppException.class, () -> updateUserService.execute(command));
        assertEquals(ErrorCode.QUOTA_BELOW_USED, ex.getError());
    }

    @Test
    void activeUserQuotaIncreaseUpdatesTenantUsageByDelta() {
        UUID tenantId = UUID.randomUUID();
        User target = userEntity(UUID.randomUUID(), tenantId, RoleCode.MEMBER, true, 100L, 20L);
        target.getTenant().setStorageQuotaBytes(1_000L);
        target.getTenant().setStorageUsedBytes(400L);
        when(userRepository.findByIdForUpdate(target.getId())).thenReturn(Optional.of(target));
        when(tenantRepository.findByIdForUpdate(tenantId)).thenReturn(Optional.of(target.getTenant()));
        when(roleRepository.findByCode(RoleCode.MEMBER)).thenReturn(Optional.of(target.getRole()));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var command = new UpdateUserUseCase.UpdateUserCommand(
                UUID.randomUUID(), tenantId, RoleCode.TENANT_ADMIN,
                target.getId(), "Updated User", RoleCode.MEMBER, 250L
        );

        var result = updateUserService.execute(command);
        assertEquals(250L, result.storageQuotaBytes());
        assertEquals(550L, target.getTenant().getStorageUsedBytes());
    }

    @Test
    void activeUserQuotaIncreaseRejectsWhenTenantRemainingInsufficient() {
        UUID tenantId = UUID.randomUUID();
        User target = userEntity(UUID.randomUUID(), tenantId, RoleCode.MEMBER, true, 100L, 20L);
        target.getTenant().setStorageQuotaBytes(300L);
        target.getTenant().setStorageUsedBytes(260L);
        when(userRepository.findByIdForUpdate(target.getId())).thenReturn(Optional.of(target));
        when(tenantRepository.findByIdForUpdate(tenantId)).thenReturn(Optional.of(target.getTenant()));
        when(roleRepository.findByCode(RoleCode.MEMBER)).thenReturn(Optional.of(target.getRole()));

        var command = new UpdateUserUseCase.UpdateUserCommand(
                UUID.randomUUID(), tenantId, RoleCode.TENANT_ADMIN,
                target.getId(), "User", RoleCode.MEMBER, 200L
        );

        AppException ex = assertThrows(AppException.class, () -> updateUserService.execute(command));
        assertEquals(ErrorCode.TENANT_QUOTA_EXCEEDED, ex.getError());
    }

    @Test
    void inactiveUserQuotaUpdateDoesNotChangeTenantUsage() {
        UUID tenantId = UUID.randomUUID();
        User target = userEntity(UUID.randomUUID(), tenantId, RoleCode.MEMBER, false, 100L, 20L);
        target.getTenant().setStorageQuotaBytes(300L);
        target.getTenant().setStorageUsedBytes(120L);
        when(userRepository.findByIdForUpdate(target.getId())).thenReturn(Optional.of(target));
        when(roleRepository.findByCode(RoleCode.MEMBER)).thenReturn(Optional.of(target.getRole()));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var command = new UpdateUserUseCase.UpdateUserCommand(
                UUID.randomUUID(), tenantId, RoleCode.TENANT_ADMIN,
                target.getId(), "Updated", RoleCode.MEMBER, 250L
        );

        var result = updateUserService.execute(command);
        assertEquals(250L, result.storageQuotaBytes());
        assertEquals(120L, target.getTenant().getStorageUsedBytes());
    }

    @Test
    void quotaReductionUsesLockedUserUsage() {
        UUID tenantId = UUID.randomUUID();
        User target = userEntity(UUID.randomUUID(), tenantId, RoleCode.MEMBER, true, 100L, 90L);
        target.getTenant().setStorageQuotaBytes(300L);
        target.getTenant().setStorageUsedBytes(100L);
        when(userRepository.findByIdForUpdate(target.getId())).thenReturn(Optional.of(target));
        when(roleRepository.findByCode(RoleCode.MEMBER)).thenReturn(Optional.of(target.getRole()));

        var command = new UpdateUserUseCase.UpdateUserCommand(
                UUID.randomUUID(), tenantId, RoleCode.TENANT_ADMIN,
                target.getId(), "User", RoleCode.MEMBER, 80L
        );

        AppException ex = assertThrows(AppException.class, () -> updateUserService.execute(command));
        assertEquals(ErrorCode.QUOTA_BELOW_USED, ex.getError());
    }

    private User userEntity(UUID userId, UUID tenantId, RoleCode roleCode, boolean isActive, long quota, long used) {
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
                .isActive(isActive)
                .storageQuotaBytes(quota)
                .storageUsedBytes(used)
                .build();
        user.setId(userId);
        return user;
    }
}
