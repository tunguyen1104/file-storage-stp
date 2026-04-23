package com.filestorage.application.usecase.user;

import com.filestorage.adapter.out.persistence.entity.Role;
import com.filestorage.adapter.out.persistence.entity.Tenant;
import com.filestorage.adapter.out.persistence.entity.User;
import com.filestorage.adapter.out.persistence.mapper.UserViewMapper;
import com.filestorage.adapter.out.persistence.repository.UserJpaRepository;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.port.in.user.ListTenantUsersUseCase;
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
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListTenantUsersServiceTest {

    @Mock
    private UserJpaRepository userRepository;

    private ListTenantUsersService listTenantUsersService;

    @BeforeEach
    void setUp() {
        UserViewMapper mapper = Mappers.getMapper(UserViewMapper.class);
        listTenantUsersService = new ListTenantUsersService(userRepository, mapper);
    }

    @Test
    void memberCannotListTenantUsers() {
        var query = new ListTenantUsersUseCase.ListTenantUsersQuery(
                UUID.randomUUID(), UUID.randomUUID(), RoleCode.MEMBER,
                null, null, null, 1, 20
        );

        assertThrows(AppException.class, () -> listTenantUsersService.execute(query));
    }

    @Test
    void tenantAdminCanListUsersInOwnTenant() {
        UUID actorTenantId = UUID.randomUUID();
        User user = createUser(UUID.randomUUID(), actorTenantId);

        when(userRepository.findAll(org.mockito.ArgumentMatchers.<Specification<User>>any(), eq(PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt")))))
                .thenReturn(new PageImpl<>(List.of(user), PageRequest.of(0, 20), 1));

        var query = new ListTenantUsersUseCase.ListTenantUsersQuery(
                UUID.randomUUID(), actorTenantId, RoleCode.TENANT_ADMIN,
                true, null, "u@", 1, 20
        );

        var result = listTenantUsersService.execute(query);
        assertEquals(1, result.data().size());
        assertEquals(actorTenantId, result.data().get(0).tenantId());
    }

    @Test
    void superAdminCanListUsersInOwnCurrentTenant() {
        UUID actorTenantId = UUID.randomUUID();
        User user = createUser(UUID.randomUUID(), actorTenantId);

        when(userRepository.findAll(org.mockito.ArgumentMatchers.<Specification<User>>any(), eq(PageRequest.of(0, 20, Sort.by(Sort.Direction.DESC, "createdAt")))))
                .thenReturn(new PageImpl<>(List.of(user), PageRequest.of(0, 20), 1));

        var query = new ListTenantUsersUseCase.ListTenantUsersQuery(
                UUID.randomUUID(), actorTenantId, RoleCode.SUPER_ADMIN,
                null, null, null, 1, 20
        );

        var result = listTenantUsersService.execute(query);
        assertEquals(1, result.data().size());
        assertEquals(actorTenantId, result.data().get(0).tenantId());
    }

    private User createUser(UUID userId, UUID tenantId) {
        Tenant tenant = Tenant.builder().name("Tenant A").slug("tenant-a").isActive(true).build();
        tenant.setId(tenantId);

        Role role = Role.builder().code(RoleCode.MEMBER).name(RoleCode.MEMBER.name()).build();
        role.setId(UUID.randomUUID());

        User creator = User.builder()
                .tenant(tenant)
                .role(role)
                .email("creator@example.com")
                .passwordHash("hash")
                .fullName("Creator")
                .isActive(true)
                .storageQuotaBytes(100L)
                .storageUsedBytes(10L)
                .build();
        creator.setId(UUID.randomUUID());
        creator.setCreatedBy(creator);

        User user = User.builder()
                .tenant(tenant)
                .role(role)
                .email("u@example.com")
                .passwordHash("hash")
                .fullName("User")
                .isActive(true)
                .storageQuotaBytes(100L)
                .storageUsedBytes(10L)
                .createdBy(creator)
                .build();
        user.setId(userId);
        return user;
    }
}


