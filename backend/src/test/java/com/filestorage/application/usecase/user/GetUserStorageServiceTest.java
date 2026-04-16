package com.filestorage.application.usecase.user;

import com.filestorage.adapter.out.persistence.entity.Role;
import com.filestorage.adapter.out.persistence.entity.Tenant;
import com.filestorage.adapter.out.persistence.entity.User;
import com.filestorage.adapter.out.persistence.mapper.UserViewMapper;
import com.filestorage.adapter.out.persistence.repository.UserJpaRepository;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.port.in.user.GetUserStorageUseCase;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetUserStorageServiceTest {

    @Mock
    private UserJpaRepository userRepository;

    private GetUserStorageService getUserStorageService;

    @BeforeEach
    void setUp() {
        UserViewMapper mapper = Mappers.getMapper(UserViewMapper.class);
        getUserStorageService = new GetUserStorageService(userRepository, mapper);
    }

    @Test
    void tenantAdminCannotReadCrossTenantStorage() {
        User target = createUser(UUID.randomUUID(), UUID.randomUUID(), RoleCode.MEMBER, 100L, 20L);
        when(userRepository.findByIdWithRoleAndTenant(target.getId())).thenReturn(Optional.of(target));

        var query = new GetUserStorageUseCase.GetUserStorageQuery(
                target.getId(), UUID.randomUUID(), UUID.randomUUID(), RoleCode.TENANT_ADMIN);

        assertThrows(AppException.class, () -> getUserStorageService.execute(query));
    }

    @Test
    void memberCanReadOwnStorage() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        User target = createUser(userId, tenantId, RoleCode.MEMBER, 100L, 25L);
        when(userRepository.findByIdWithRoleAndTenant(userId)).thenReturn(Optional.of(target));

        var query = new GetUserStorageUseCase.GetUserStorageQuery(userId, userId, tenantId, RoleCode.MEMBER);
        var result = getUserStorageService.execute(query);

        assertEquals(75L, result.storageAvailableBytes());
        assertEquals(25D, result.usagePercent());
    }

    private User createUser(UUID userId, UUID tenantId, RoleCode roleCode, long quota, long used) {
        Tenant tenant = Tenant.builder().name("T").slug("t").isActive(true).build();
        tenant.setId(tenantId);

        Role role = Role.builder().code(roleCode).name(roleCode.name()).build();
        role.setId(UUID.randomUUID());

        User user = User.builder()
                .tenant(tenant)
                .role(role)
                .email("u@example.com")
                .passwordHash("hash")
                .fullName("User")
                .isActive(true)
                .storageQuotaBytes(quota)
                .storageUsedBytes(used)
                .build();
        user.setId(userId);
        return user;
    }
}

