package com.filestorage.application.usecase.user;

import com.filestorage.adapter.out.persistence.entity.Role;
import com.filestorage.adapter.out.persistence.entity.Tenant;
import com.filestorage.adapter.out.persistence.entity.User;
import com.filestorage.adapter.out.persistence.mapper.UserViewMapper;
import com.filestorage.adapter.out.persistence.repository.UserJpaRepository;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.port.in.user.GetUserUseCase;
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
class GetUserServiceTest {

    @Mock
    private UserJpaRepository userRepository;

    private GetUserService getUserService;

    @BeforeEach
    void setUp() {
        UserViewMapper mapper = Mappers.getMapper(UserViewMapper.class);
        getUserService = new GetUserService(userRepository, mapper);
    }

    @Test
    void memberCannotReadAnotherUser() {
        UUID tenantId = UUID.randomUUID();
        User target = createUser(UUID.randomUUID(), tenantId, RoleCode.MEMBER);
        when(userRepository.findByIdWithRoleAndTenant(target.getId())).thenReturn(Optional.of(target));

        var query = new GetUserUseCase.GetUserQuery(target.getId(), UUID.randomUUID(), tenantId, RoleCode.MEMBER);
        assertThrows(AppException.class, () -> getUserService.execute(query));
    }

    @Test
    void superAdminCanReadCrossTenant() {
        UUID targetTenantId = UUID.randomUUID();
        User target = createUser(UUID.randomUUID(), targetTenantId, RoleCode.MEMBER);
        when(userRepository.findByIdWithRoleAndTenant(target.getId())).thenReturn(Optional.of(target));

        var query = new GetUserUseCase.GetUserQuery(target.getId(), UUID.randomUUID(), UUID.randomUUID(), RoleCode.SUPER_ADMIN);
        var result = getUserService.execute(query);

        assertEquals(target.getId(), result.id());
        assertEquals(targetTenantId, result.tenantId());
    }

    private User createUser(UUID userId, UUID tenantId, RoleCode roleCode) {
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
                .storageQuotaBytes(100L)
                .storageUsedBytes(10L)
                .build();
        user.setId(userId);
        return user;
    }
}

