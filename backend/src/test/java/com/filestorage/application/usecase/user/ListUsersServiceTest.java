package com.filestorage.application.usecase.user;

import com.filestorage.adapter.out.persistence.entity.Role;
import com.filestorage.adapter.out.persistence.entity.Tenant;
import com.filestorage.adapter.out.persistence.entity.User;
import com.filestorage.adapter.out.persistence.mapper.UserViewMapper;
import com.filestorage.adapter.out.persistence.repository.UserJpaRepository;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.port.in.user.ListUsersUseCase;
import com.filestorage.shared.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListUsersServiceTest {

    @Mock
    private UserJpaRepository userRepository;

    private ListUsersService listUsersService;

    @BeforeEach
    void setUp() {
        UserViewMapper mapper = Mappers.getMapper(UserViewMapper.class);
        listUsersService = new ListUsersService(userRepository, mapper);
    }

    @Test
    void memberCannotListUsers() {
        var query = new ListUsersUseCase.ListUsersQuery(
                UUID.randomUUID(), UUID.randomUUID(), RoleCode.MEMBER,
                null, null, null, null, 1, 20
        );

        assertThrows(AppException.class, () -> listUsersService.execute(query));
    }

    @Test
    void tenantAdminCannotListUsers() {
        var query = new ListUsersUseCase.ListUsersQuery(
                UUID.randomUUID(), UUID.randomUUID(), RoleCode.TENANT_ADMIN,
                null, null, null, null, 1, 20
        );

        assertThrows(AppException.class, () -> listUsersService.execute(query));
    }

    @Test
    void superAdminCanListUsers() {
        UUID targetTenantId = UUID.randomUUID();
        User user = createUser(UUID.randomUUID(), targetTenantId, RoleCode.MEMBER);

        when(userRepository.findAll(any(), eq(PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt")))))
                .thenReturn(new PageImpl<>(List.of(user), PageRequest.of(0, 20), 1));

        var query = new ListUsersUseCase.ListUsersQuery(
                UUID.randomUUID(), UUID.randomUUID(), RoleCode.SUPER_ADMIN,
                targetTenantId, true, null, "u@", 1, 20
        );

        var result = listUsersService.execute(query);
        assertEquals(1, result.data().size());
        assertEquals(targetTenantId, result.data().get(0).tenantId());
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

