package com.filestorage.application.usecase.tenant;

import com.filestorage.adapter.out.persistence.entity.Tenant;
import com.filestorage.adapter.out.persistence.mapper.TenantViewMapper;
import com.filestorage.adapter.out.persistence.repository.TenantJpaRepository;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.port.in.tenant.ActivateTenantUseCase;
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
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ActivateTenantServiceTest {

    @Mock
    private TenantJpaRepository tenantRepository;

    private ActivateTenantService activateTenantService;

    @BeforeEach
    void setUp() {
        TenantViewMapper mapper = Mappers.getMapper(TenantViewMapper.class);
        activateTenantService = new ActivateTenantService(tenantRepository, mapper);
    }

    @Test
    void activateSuccess() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = Tenant.builder()
                .name("Acme")
                .slug("acme")
                .isActive(false)
                .storageQuotaBytes(100L)
                .storageUsedBytes(0L)
                .build();
        tenant.setId(tenantId);
        when(tenantRepository.findByIdForUpdate(tenantId)).thenReturn(Optional.of(tenant));

        var command = new ActivateTenantUseCase.ActivateTenantCommand(UUID.randomUUID(), RoleCode.SUPER_ADMIN, tenantId);
        var result = activateTenantService.execute(command);

        assertTrue(result.isActive());
        verify(tenantRepository).save(tenant);
    }

    @Test
    void alreadyActiveReturnsCurrentTenant() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = Tenant.builder()
                .name("Acme")
                .slug("acme")
                .isActive(true)
                .storageQuotaBytes(100L)
                .storageUsedBytes(0L)
                .build();
        tenant.setId(tenantId);
        when(tenantRepository.findByIdForUpdate(tenantId)).thenReturn(Optional.of(tenant));

        var command = new ActivateTenantUseCase.ActivateTenantCommand(UUID.randomUUID(), RoleCode.SUPER_ADMIN, tenantId);
        var result = activateTenantService.execute(command);

        assertTrue(result.isActive());
        verify(tenantRepository, never()).save(any());
    }

    @Test
    void tenantNotFoundThrows() {
        UUID tenantId = UUID.randomUUID();
        when(tenantRepository.findByIdForUpdate(tenantId)).thenReturn(Optional.empty());

        var command = new ActivateTenantUseCase.ActivateTenantCommand(UUID.randomUUID(), RoleCode.SUPER_ADMIN, tenantId);
        AppException exception = assertThrows(AppException.class, () -> activateTenantService.execute(command));

        assertEquals(ErrorCode.TENANT_NOT_FOUND, exception.getError());
    }

    @Test
    void nonSuperAdminForbidden() {
        UUID tenantId = UUID.randomUUID();
        var command = new ActivateTenantUseCase.ActivateTenantCommand(UUID.randomUUID(), RoleCode.TENANT_ADMIN, tenantId);
        AppException exception = assertThrows(AppException.class, () -> activateTenantService.execute(command));

        assertEquals(ErrorCode.FORBIDDEN, exception.getError());
        verifyNoInteractions(tenantRepository);
    }
}
