package com.filestorage.application.usecase.tenant;

import com.filestorage.adapter.out.persistence.entity.Tenant;
import com.filestorage.adapter.out.persistence.mapper.TenantViewMapper;
import com.filestorage.adapter.out.persistence.repository.TenantJpaRepository;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.port.in.tenant.ListTenantsUseCase;
import com.filestorage.shared.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mapstruct.factory.Mappers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListTenantsServiceTest {

    @Mock
    private TenantJpaRepository tenantRepository;

    private ListTenantsService listTenantsService;

    @BeforeEach
    void setUp() {
        TenantViewMapper mapper = Mappers.getMapper(TenantViewMapper.class);
        listTenantsService = new ListTenantsService(tenantRepository, mapper);
    }

    @Test
    void nonSuperAdminForbidden() {
        var query = new ListTenantsUseCase.ListTenantsQuery(
                UUID.randomUUID(), RoleCode.TENANT_ADMIN, null, null, 1, 20
        );

        assertThrows(AppException.class, () -> listTenantsService.execute(query));
    }

    @Test
    void listSuccess() {
        Tenant tenant = Tenant.builder().name("Acme").slug("acme").isActive(true).storageQuotaBytes(100L).storageUsedBytes(10L).build();
        tenant.setId(UUID.randomUUID());

        when(tenantRepository.findAll(any(Specification.class), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(tenant), PageRequest.of(0, 20), 1));

        var query = new ListTenantsUseCase.ListTenantsQuery(
                UUID.randomUUID(), RoleCode.SUPER_ADMIN, null, null, 1, 20
        );

        var result = listTenantsService.execute(query);
        assertEquals(1, result.data().size());
        assertEquals("acme", result.data().get(0).slug());
    }
}

