package com.filestorage.application.usecase.file;

import com.filestorage.adapter.out.persistence.entity.File;
import com.filestorage.adapter.out.persistence.entity.Tenant;
import com.filestorage.adapter.out.persistence.entity.User;
import com.filestorage.adapter.out.persistence.mapper.FileViewMapper;
import com.filestorage.adapter.out.persistence.repository.FileJpaRepository;
import com.filestorage.domain.model.enums.FileSortBy;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.enums.SortDirection;
import com.filestorage.domain.model.file.FileView;
import com.filestorage.domain.port.in.file.ListFileMetadataUseCase;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentMatchers;
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
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListFileMetadataServiceTest {

    @Mock
    private FileJpaRepository fileRepository;
    @Mock
    private FileSupport fileSupport;
    @Mock
    private FileViewMapper fileViewMapper;

    private ListFileMetadataService service;

    @BeforeEach
    void setUp() {
        service = new ListFileMetadataService(fileRepository, fileSupport, fileViewMapper);
    }

    @Test
    void memberUploadedByMismatchReturns403() {
        UUID actorUserId = UUID.randomUUID();
        UUID actorTenantId = UUID.randomUUID();
        when(fileSupport.resolveActor(actorUserId, actorTenantId)).thenReturn(actor(actorUserId, actorTenantId));

        var query = new ListFileMetadataUseCase.ListFileMetadataQuery(
                actorUserId,
                actorTenantId,
                RoleCode.MEMBER,
                null,
                null,
                UUID.randomUUID(),
                null,
                null,
                1,
                20,
                List.of(FileSortBy.CREATED_AT),
                List.of(SortDirection.DESC)
        );

        AppException ex = assertThrows(AppException.class, () -> service.execute(query));
        assertEquals(ErrorCode.FORBIDDEN, ex.getError());
    }

    @Test
    void tenantAdminCanListPagedMetadata() {
        UUID actorUserId = UUID.randomUUID();
        UUID actorTenantId = UUID.randomUUID();
        when(fileSupport.resolveActor(actorUserId, actorTenantId)).thenReturn(actor(actorUserId, actorTenantId));

        File file = File.builder().originalName("report.pdf").build();
        file.setId(UUID.randomUUID());
        when(fileRepository.findAll(
                ArgumentMatchers.<Specification<File>>any(),
                eq(PageRequest.of(0, 20, Sort.by(new Sort.Order(Sort.Direction.DESC, "createdAt"))))
        )).thenReturn(new PageImpl<>(List.of(file)));

        FileView view = new FileView(file.getId(), "report.pdf", "application/pdf", 10L, com.filestorage.domain.model.enums.FileStatus.ACTIVE, null, actorTenantId, "k", actorUserId, "User", null, null, false, null, null, null, null);
        when(fileViewMapper.toFileView(file)).thenReturn(view);

        var query = new ListFileMetadataUseCase.ListFileMetadataQuery(
                actorUserId,
                actorTenantId,
                RoleCode.TENANT_ADMIN,
                null,
                null,
                null,
                null,
                null,
                1,
                20,
                List.of(FileSortBy.CREATED_AT),
                List.of(SortDirection.DESC)
        );

        var result = service.execute(query);

        assertEquals(1, result.total());
        assertEquals("report.pdf", result.data().get(0).originalName());
    }

    @Test
    void tenantAdminCannotUseOtherTenantFilter() {
        UUID actorUserId = UUID.randomUUID();
        UUID actorTenantId = UUID.randomUUID();
        when(fileSupport.resolveActor(actorUserId, actorTenantId)).thenReturn(actor(actorUserId, actorTenantId));

        var query = new ListFileMetadataUseCase.ListFileMetadataQuery(
                actorUserId,
                actorTenantId,
                RoleCode.TENANT_ADMIN,
                null,
                null,
                null,
                UUID.randomUUID(),
                null,
                1,
                20,
                List.of(FileSortBy.CREATED_AT),
                List.of(SortDirection.DESC)
        );

        AppException ex = assertThrows(AppException.class, () -> service.execute(query));
        assertEquals(ErrorCode.FORBIDDEN, ex.getError());
    }

    @Test
    void laterSortFieldHasHigherPriorityAndDefaultsDirection() {
        UUID actorUserId = UUID.randomUUID();
        UUID actorTenantId = UUID.randomUUID();
        when(fileSupport.resolveActor(actorUserId, actorTenantId)).thenReturn(actor(actorUserId, actorTenantId));

        File file = File.builder().originalName("report.pdf").build();
        file.setId(UUID.randomUUID());
        when(fileRepository.findAll(
                ArgumentMatchers.<Specification<File>>any(),
                eq(PageRequest.of(0, 20, Sort.by(
                        new Sort.Order(Sort.Direction.DESC, "createdAt"),
                        new Sort.Order(Sort.Direction.ASC, "originalName")
                )))
        )).thenReturn(new PageImpl<>(List.of(file)));

        var query = new ListFileMetadataUseCase.ListFileMetadataQuery(
                actorUserId,
                actorTenantId,
                RoleCode.TENANT_ADMIN,
                null,
                null,
                null,
                null,
                null,
                1,
                20,
                List.of(FileSortBy.ORIGINAL_NAME, FileSortBy.CREATED_AT),
                List.of(SortDirection.ASC)
        );

        service.execute(query);
    }

    private User actor(UUID userId, UUID tenantId) {
        Tenant tenant = Tenant.builder().name("Tenant").slug("tenant").isActive(true).build();
        tenant.setId(tenantId);

        User actor = User.builder()
                .tenant(tenant)
                .email("actor@example.com")
                .passwordHash("hash")
                .fullName("Actor")
                .build();
        actor.setId(userId);
        return actor;
    }
}
