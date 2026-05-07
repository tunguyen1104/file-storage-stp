package com.filestorage.application.usecase.tenant;

import com.filestorage.adapter.out.persistence.entity.Tenant;
import com.filestorage.adapter.out.persistence.mapper.TenantViewMapper;
import com.filestorage.adapter.out.persistence.repository.TenantJpaRepository;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.port.in.tenant.UpdateTenantUseCase;
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
class UpdateTenantServiceTest {

    @Mock
    private TenantJpaRepository tenantRepository;

    private UpdateTenantService updateTenantService;

    @BeforeEach
    void setUp() {
        TenantViewMapper mapper = Mappers.getMapper(TenantViewMapper.class);
        updateTenantService = new UpdateTenantService(tenantRepository, mapper);
    }

    @Test
    void quotaBelowUsedForbidden() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = Tenant.builder().name("Acme").slug("acme").isActive(true).storageQuotaBytes(100L).storageUsedBytes(60L).build();
        tenant.setId(tenantId);
        when(tenantRepository.findByIdForUpdate(tenantId)).thenReturn(Optional.of(tenant));

        var command = new UpdateTenantUseCase.UpdateTenantCommand(
                UUID.randomUUID(), RoleCode.SUPER_ADMIN, tenantId, "Acme", "acme", 50L
        );

        assertThrows(AppException.class, () -> updateTenantService.execute(command));
    }

    @Test
    void updateSuccess() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = Tenant.builder().name("Acme").slug("acme").isActive(true).storageQuotaBytes(100L).storageUsedBytes(10L).build();
        tenant.setId(tenantId);
        when(tenantRepository.findByIdForUpdate(tenantId)).thenReturn(Optional.of(tenant));
        when(tenantRepository.existsBySlugAndIdNot("acme-new", tenantId)).thenReturn(false);
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> invocation.getArgument(0));

        var command = new UpdateTenantUseCase.UpdateTenantCommand(
                UUID.randomUUID(), RoleCode.SUPER_ADMIN, tenantId, "Acme New", "acme-new", 120L
        );

        var result = updateTenantService.execute(command);
        assertEquals("acme-new", result.slug());
        assertEquals(120L, result.storageQuotaBytes());
    }
}
