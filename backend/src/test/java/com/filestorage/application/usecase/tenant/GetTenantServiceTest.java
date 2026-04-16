package com.filestorage.application.usecase.tenant;

import com.filestorage.adapter.out.persistence.entity.Tenant;
import com.filestorage.adapter.out.persistence.mapper.TenantViewMapper;
import com.filestorage.adapter.out.persistence.repository.TenantJpaRepository;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.port.in.tenant.GetTenantUseCase;
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
class GetTenantServiceTest {

    @Mock
    private TenantJpaRepository tenantRepository;

    private GetTenantService getTenantService;

    @BeforeEach
    void setUp() {
        TenantViewMapper mapper = Mappers.getMapper(TenantViewMapper.class);
        getTenantService = new GetTenantService(tenantRepository, mapper);
    }

    @Test
    void getSuccess() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = Tenant.builder().name("Acme").slug("acme").isActive(true).storageQuotaBytes(100L).storageUsedBytes(10L).build();
        tenant.setId(tenantId);
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

        var query = new GetTenantUseCase.GetTenantQuery(UUID.randomUUID(), RoleCode.SUPER_ADMIN, tenantId);
        var result = getTenantService.execute(query);
        assertEquals(tenantId, result.id());
    }

    @Test
    void nonSuperAdminForbidden() {
        var query = new GetTenantUseCase.GetTenantQuery(UUID.randomUUID(), RoleCode.TENANT_ADMIN, UUID.randomUUID());
        assertThrows(AppException.class, () -> getTenantService.execute(query));
    }
}

