package com.filestorage.application.usecase.tenant;

import com.filestorage.adapter.out.persistence.entity.Tenant;
import com.filestorage.adapter.out.persistence.mapper.TenantViewMapper;
import com.filestorage.adapter.out.persistence.repository.TenantJpaRepository;
import com.filestorage.adapter.out.persistence.repository.UserJpaRepository;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.port.in.tenant.GetTenantUseCase;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetTenantServiceTest {

    @Mock
    private TenantJpaRepository tenantRepository;
    @Mock
    private UserJpaRepository userRepository;

    private GetTenantService getTenantService;

    @BeforeEach
    void setUp() {
        TenantViewMapper mapper = Mappers.getMapper(TenantViewMapper.class);
        getTenantService = new GetTenantService(tenantRepository, userRepository, mapper);
    }

    @Test
    void superAdminCanGetAnyTenantWithUserCount() {
        UUID actorTenantId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = Tenant.builder().name("Acme").slug("acme").isActive(true).storageQuotaBytes(100L).storageUsedBytes(10L).build();
        tenant.setId(tenantId);
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(userRepository.countUsersByTenantIds(List.of(tenantId))).thenReturn(List.of(userCount(tenantId, 3L)));

        var query = new GetTenantUseCase.GetTenantQuery(UUID.randomUUID(), actorTenantId, RoleCode.SUPER_ADMIN, tenantId);
        var result = getTenantService.execute(query);
        assertEquals(tenantId, result.id());
        assertEquals(3L, result.userCount());
    }

    @Test
    void tenantAdminCanGetOwnTenant() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = Tenant.builder().name("Acme").slug("acme").isActive(true).storageQuotaBytes(100L).storageUsedBytes(10L).build();
        tenant.setId(tenantId);
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));
        when(userRepository.countUsersByTenantIds(List.of(tenantId))).thenReturn(List.of(userCount(tenantId, 2L)));

        var query = new GetTenantUseCase.GetTenantQuery(UUID.randomUUID(), tenantId, RoleCode.TENANT_ADMIN, tenantId);
        var result = getTenantService.execute(query);
        assertEquals(tenantId, result.id());
        assertEquals(2L, result.userCount());
    }

    @Test
    void tenantAdminCannotGetOtherTenant() {
        var query = new GetTenantUseCase.GetTenantQuery(
                UUID.randomUUID(),
                UUID.randomUUID(),
                RoleCode.TENANT_ADMIN,
                UUID.randomUUID()
        );

        AppException ex = assertThrows(AppException.class, () -> getTenantService.execute(query));
        assertEquals(ErrorCode.FORBIDDEN, ex.getError());
    }

    @Test
    void memberForbidden() {
        UUID tenantId = UUID.randomUUID();
        var query = new GetTenantUseCase.GetTenantQuery(UUID.randomUUID(), tenantId, RoleCode.MEMBER, tenantId);

        AppException ex = assertThrows(AppException.class, () -> getTenantService.execute(query));
        assertEquals(ErrorCode.FORBIDDEN, ex.getError());
    }

    @Test
    void notFound() {
        UUID tenantId = UUID.randomUUID();
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.empty());

        var query = new GetTenantUseCase.GetTenantQuery(UUID.randomUUID(), UUID.randomUUID(), RoleCode.SUPER_ADMIN, tenantId);

        AppException ex = assertThrows(AppException.class, () -> getTenantService.execute(query));
        assertEquals(ErrorCode.TENANT_NOT_FOUND, ex.getError());
    }

    private UserJpaRepository.TenantUserCount userCount(UUID tenantId, long userCount) {
        return new UserJpaRepository.TenantUserCount() {
            @Override
            public UUID getTenantId() {
                return tenantId;
            }

            @Override
            public long getUserCount() {
                return userCount;
            }
        };
    }
}
