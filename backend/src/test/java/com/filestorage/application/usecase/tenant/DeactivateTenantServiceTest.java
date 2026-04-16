package com.filestorage.application.usecase.tenant;

import com.filestorage.adapter.out.persistence.entity.Tenant;
import com.filestorage.adapter.out.persistence.mapper.TenantViewMapper;
import com.filestorage.adapter.out.persistence.repository.TenantJpaRepository;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.port.in.tenant.DeactivateTenantUseCase;
import com.filestorage.shared.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeactivateTenantServiceTest {

    @Mock
    private TenantJpaRepository tenantRepository;

    private DeactivateTenantService deactivateTenantService;

    @BeforeEach
    void setUp() {
        TenantViewMapper mapper = Mappers.getMapper(TenantViewMapper.class);
        deactivateTenantService = new DeactivateTenantService(tenantRepository, mapper);
    }

    @Test
    void cannotDeactivateSystemTenant() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = Tenant.builder().name("System").slug("system").isActive(true).storageQuotaBytes(1L).storageUsedBytes(0L).build();
        tenant.setId(tenantId);
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

        var command = new DeactivateTenantUseCase.DeactivateTenantCommand(UUID.randomUUID(), RoleCode.SUPER_ADMIN, tenantId);
        assertThrows(AppException.class, () -> deactivateTenantService.execute(command));
    }

    @Test
    void deactivateSuccess() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = Tenant.builder().name("Acme").slug("acme").isActive(true).storageQuotaBytes(100L).storageUsedBytes(0L).build();
        tenant.setId(tenantId);
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

        var command = new DeactivateTenantUseCase.DeactivateTenantCommand(UUID.randomUUID(), RoleCode.SUPER_ADMIN, tenantId);
        var result = deactivateTenantService.execute(command);
        assertFalse(result.isActive());
    }
}

