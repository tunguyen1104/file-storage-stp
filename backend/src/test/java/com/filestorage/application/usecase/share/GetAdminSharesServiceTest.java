package com.filestorage.application.usecase.share;

import com.filestorage.adapter.out.persistence.entity.Role;
import com.filestorage.adapter.out.persistence.entity.Tenant;
import com.filestorage.adapter.out.persistence.entity.User;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.enums.ShareSortBy;
import com.filestorage.domain.model.enums.ShareStatus;
import com.filestorage.domain.model.enums.SortDirection;
import com.filestorage.domain.model.share.PaginationView;
import com.filestorage.domain.port.in.share.GetAdminSharesQuery;
import com.filestorage.domain.port.out.ShareRepositoryPort;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetAdminSharesServiceTest {

    @Mock
    private ShareRepositoryPort shareRepository;
    @Mock
    private ShareServiceSupport support;

    private GetAdminSharesService service;

    @BeforeEach
    void setUp() {
        service = new GetAdminSharesService(shareRepository, support);

        lenient().when(support.normalizePage(anyInt()))
                .thenAnswer(invocation -> Math.max(1, invocation.getArgument(0)));
        lenient().when(support.normalizeLimit(anyInt()))
                .thenAnswer(invocation -> Math.min(100, Math.max(1, invocation.getArgument(0))));
        lenient().when(support.normalizeResourceType(any()))
                .thenAnswer(invocation -> {
                    String resourceType = invocation.getArgument(0);
                    return resourceType == null || resourceType.isBlank()
                            ? null
                            : resourceType.trim().toLowerCase(Locale.ROOT);
                });
        lenient().when(support.toPagination(any()))
                .thenAnswer(invocation -> {
                    Page<?> page = invocation.getArgument(0);
                    return new PaginationView(
                            page.getNumber() + 1,
                            page.getSize(),
                            page.getTotalElements(),
                            page.getTotalPages()
                    );
                });
        lenient().when(shareRepository.findAdminShares(any(), any(), any(), any(), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 20), 0));
    }

    @Test
    void tenantAdminListsOnlyOwnTenantWithDefaultSortAndAllStatuses() {
        UUID adminId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        User admin = user(adminId, tenantId, RoleCode.TENANT_ADMIN);

        when(support.resolveActor(adminId)).thenReturn(admin);
        when(support.tenantId(admin)).thenReturn(tenantId);

        service.getAdminShares(new GetAdminSharesQuery(
                adminId,
                null,
                null,
                null,
                null,
                1,
                20,
                null,
                null
        ));

        verify(shareRepository).findAdminShares(
                eq(List.of(tenantId)),
                isNull(),
                isNull(),
                isNull(),
                eq(PageRequest.of(0, 20, Sort.by(new Sort.Order(Sort.Direction.DESC, "createdAt"))))
        );
    }

    @Test
    void superAdminCanFilterMultipleTenantsStatusesAndSortFields() {
        UUID adminId = UUID.randomUUID();
        UUID adminTenantId = UUID.randomUUID();
        UUID firstTenantId = UUID.randomUUID();
        UUID secondTenantId = UUID.randomUUID();
        UUID firstSharedById = UUID.randomUUID();
        UUID secondSharedById = UUID.randomUUID();
        User admin = user(adminId, adminTenantId, RoleCode.SUPER_ADMIN);

        when(support.resolveActor(adminId)).thenReturn(admin);

        service.getAdminShares(new GetAdminSharesQuery(
                adminId,
                List.of(firstTenantId, secondTenantId),
                List.of(firstSharedById, secondSharedById),
                "file",
                List.of(ShareStatus.ACTIVE, ShareStatus.REVOKED),
                1,
                20,
                List.of(ShareSortBy.STATUS, ShareSortBy.EXPIRES_AT),
                List.of(SortDirection.ASC)
        ));

        verify(shareRepository).findAdminShares(
                eq(List.of(firstTenantId, secondTenantId)),
                eq(List.of(firstSharedById, secondSharedById)),
                eq("file"),
                eq(List.of(ShareStatus.ACTIVE, ShareStatus.REVOKED)),
                eq(PageRequest.of(0, 20, Sort.by(
                        new Sort.Order(Sort.Direction.DESC, "expiresAt"),
                        new Sort.Order(Sort.Direction.ASC, "status")
                )))
        );
    }

    @Test
    void tenantAdminCannotFilterOtherTenant() {
        UUID adminId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        User admin = user(adminId, tenantId, RoleCode.TENANT_ADMIN);

        when(support.resolveActor(adminId)).thenReturn(admin);
        when(support.tenantId(admin)).thenReturn(tenantId);

        AppException ex = assertThrows(AppException.class, () -> service.getAdminShares(new GetAdminSharesQuery(
                adminId,
                List.of(UUID.randomUUID()),
                null,
                null,
                null,
                1,
                20,
                null,
                null
        )));

        assertEquals(ErrorCode.FORBIDDEN, ex.getError());
        verify(shareRepository, never()).findAdminShares(any(), any(), any(), any(), any());
    }

    @Test
    void tenantAdminCannotFilterSharedByOutsideOwnTenant() {
        UUID adminId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID sharedById = UUID.randomUUID();
        User admin = user(adminId, tenantId, RoleCode.TENANT_ADMIN);

        when(support.resolveActor(adminId)).thenReturn(admin);
        when(support.tenantId(admin)).thenReturn(tenantId);
        when(support.allUsersBelongToTenant(List.of(sharedById), tenantId)).thenReturn(false);

        AppException ex = assertThrows(AppException.class, () -> service.getAdminShares(new GetAdminSharesQuery(
                adminId,
                null,
                List.of(sharedById),
                null,
                null,
                1,
                20,
                null,
                null
        )));

        assertEquals(ErrorCode.SHARED_BY_OUT_OF_TENANT, ex.getError());
        verify(shareRepository, never()).findAdminShares(any(), any(), any(), any(), any());
    }

    @Test
    void memberCannotListAdminShares() {
        UUID actorId = UUID.randomUUID();
        User actor = user(actorId, UUID.randomUUID(), RoleCode.MEMBER);

        when(support.resolveActor(actorId)).thenReturn(actor);

        AppException ex = assertThrows(AppException.class, () -> service.getAdminShares(new GetAdminSharesQuery(
                actorId,
                null,
                null,
                null,
                null,
                1,
                20,
                null,
                null
        )));

        assertEquals(ErrorCode.FORBIDDEN_ADMIN_REQUIRED, ex.getError());
        verify(shareRepository, never()).findAdminShares(any(), any(), any(), any(), any());
    }

    private User user(UUID userId, UUID tenantId, RoleCode roleCode) {
        Tenant tenant = Tenant.builder().name("Tenant").slug("tenant").build();
        tenant.setId(tenantId);
        Role role = Role.builder().code(roleCode).name(roleCode.name()).build();
        User user = User.builder()
                .tenant(tenant)
                .role(role)
                .email("admin@example.com")
                .passwordHash("hash")
                .fullName("Admin")
                .build();
        user.setId(userId);
        return user;
    }
}
