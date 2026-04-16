package com.filestorage.application.usecase.tenant;

import com.filestorage.adapter.out.persistence.entity.Tenant;
import com.filestorage.adapter.out.persistence.mapper.TenantViewMapper;
import com.filestorage.adapter.out.persistence.repository.TenantJpaRepository;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.port.in.tenant.CreateTenantUseCase;
import com.filestorage.shared.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateTenantServiceTest {

    @Mock
    private TenantJpaRepository tenantRepository;

    private CreateTenantService createTenantService;

    @BeforeEach
    void setUp() {
        TenantViewMapper mapper = Mappers.getMapper(TenantViewMapper.class);
        createTenantService = new CreateTenantService(tenantRepository, mapper);
    }

    @Test
    void nonSuperAdminCannotCreateTenant() {
        var command = new CreateTenantUseCase.CreateTenantCommand(
                UUID.randomUUID(), RoleCode.TENANT_ADMIN, "Acme", "acme", null
        );

        assertThrows(AppException.class, () -> createTenantService.execute(command));
    }

    @Test
    void slugAlreadyExists() {
        when(tenantRepository.existsBySlug("acme")).thenReturn(true);

        var command = new CreateTenantUseCase.CreateTenantCommand(
                UUID.randomUUID(), RoleCode.SUPER_ADMIN, "Acme", "acme", null
        );

        assertThrows(AppException.class, () -> createTenantService.execute(command));
    }

    @Test
    void createSuccess() {
        when(tenantRepository.existsBySlug("acme")).thenReturn(false);
        when(tenantRepository.save(any(Tenant.class))).thenAnswer(invocation -> {
            Tenant tenant = invocation.getArgument(0);
            tenant.setId(UUID.randomUUID());
            return tenant;
        });

        var command = new CreateTenantUseCase.CreateTenantCommand(
                UUID.randomUUID(), RoleCode.SUPER_ADMIN, "Acme", "acme", 100L
        );

        var result = createTenantService.execute(command);
        assertEquals("acme", result.slug());
        assertEquals(100L, result.storageQuotaBytes());
    }
}


