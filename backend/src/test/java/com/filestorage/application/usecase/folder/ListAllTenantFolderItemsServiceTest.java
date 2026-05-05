package com.filestorage.application.usecase.folder;

import com.filestorage.adapter.out.persistence.entity.File;
import com.filestorage.adapter.out.persistence.entity.Folder;
import com.filestorage.adapter.out.persistence.entity.Tenant;
import com.filestorage.adapter.out.persistence.entity.User;
import com.filestorage.adapter.out.persistence.repository.FileJpaRepository;
import com.filestorage.adapter.out.persistence.repository.FolderJpaRepository;
import com.filestorage.domain.model.enums.AdminFolderItemSortBy;
import com.filestorage.domain.model.enums.FileStatus;
import com.filestorage.domain.model.enums.FolderItemType;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.folder.AdminFolderItemPageView;
import com.filestorage.domain.port.in.folder.ListAllTenantFolderItemsUseCase;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.ArgumentMatchers;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListAllTenantFolderItemsServiceTest {

    @Mock
    private FolderJpaRepository folderRepository;
    @Mock
    private FileJpaRepository fileRepository;
    @Mock
    private FolderSupport folderSupport;

    private ListAllTenantFolderItemsService service;
    private UUID actorUserId;
    private UUID actorTenantId;
    private User actor;

    @BeforeEach
    void setUp() {
        service = new ListAllTenantFolderItemsService(folderRepository, fileRepository, folderSupport);
        actorUserId = UUID.randomUUID();
        actorTenantId = UUID.randomUUID();
        actor = user(actorUserId, tenant(actorTenantId, "System Tenant"), "Super Admin");
    }

    @Test
    void mixedItemsAlwaysReturnFoldersBeforeFiles() {
        when(folderSupport.resolveActor(actorUserId, actorTenantId)).thenReturn(actor);

        Tenant tenant = tenant(UUID.randomUUID(), "Tenant A");
        User creator = user(UUID.randomUUID(), tenant, "Creator");
        Folder folderA = folder(UUID.randomUUID(), tenant, creator, "Folder A", null);
        Folder folderB = folder(UUID.randomUUID(), tenant, creator, "Folder B", null);
        File file = file(UUID.randomUUID(), tenant, creator, "file.pdf", null);

        when(folderRepository.count(ArgumentMatchers.<Specification<Folder>>any())).thenReturn(2L);
        when(fileRepository.count(ArgumentMatchers.<Specification<File>>any())).thenReturn(1L);
        when(folderRepository.findAll(
                ArgumentMatchers.<Specification<Folder>>any(),
                ArgumentMatchers.any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(folderA, folderB)));
        when(fileRepository.findAll(
                ArgumentMatchers.<Specification<File>>any(),
                ArgumentMatchers.any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(file)));

        AdminFolderItemPageView result = service.execute(query(null, null, null, 1, 10));

        assertEquals(3L, result.total());
        assertEquals(FolderItemType.FOLDER, result.data().get(0).type());
        assertEquals(FolderItemType.FOLDER, result.data().get(1).type());
        assertEquals(FolderItemType.FILE, result.data().get(2).type());
    }

    @Test
    void statusWithoutTypeListsFilesOnly() {
        when(folderSupport.resolveActor(actorUserId, actorTenantId)).thenReturn(actor);

        Tenant tenant = tenant(UUID.randomUUID(), "Tenant A");
        User creator = user(UUID.randomUUID(), tenant, "Creator");
        File file = file(UUID.randomUUID(), tenant, creator, "file.pdf", null);

        when(fileRepository.findAll(
                ArgumentMatchers.<Specification<File>>any(),
                ArgumentMatchers.any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(file)));

        AdminFolderItemPageView result = service.execute(query(null, FileStatus.ACTIVE, null, 1, 10));

        assertEquals(1L, result.total());
        assertEquals(FolderItemType.FILE, result.data().get(0).type());
        verify(folderRepository, never()).count(ArgumentMatchers.<Specification<Folder>>any());
        verify(folderRepository, never()).findAll(
                ArgumentMatchers.<Specification<Folder>>any(),
                ArgumentMatchers.any(Pageable.class)
        );
    }

    @Test
    void folderTypeWithStatusIsInvalid() {
        when(folderSupport.resolveActor(actorUserId, actorTenantId)).thenReturn(actor);

        AppException ex = assertThrows(AppException.class, () ->
                service.execute(query(FolderItemType.FOLDER, FileStatus.ACTIVE, null, 1, 10))
        );

        assertEquals(ErrorCode.INVALID_PAYLOAD, ex.getError());
    }

    @Test
    void nonSuperAdminIsForbidden() {
        when(folderSupport.resolveActor(actorUserId, actorTenantId)).thenReturn(actor);

        AppException ex = assertThrows(AppException.class, () ->
                service.execute(new ListAllTenantFolderItemsUseCase.ListAllTenantFolderItemsQuery(
                        actorUserId,
                        actorTenantId,
                        RoleCode.TENANT_ADMIN,
                        null,
                        null,
                        null,
                        null,
                        null,
                        null,
                        1,
                        10,
                        null,
                        null
                ))
        );

        assertEquals(ErrorCode.FORBIDDEN, ex.getError());
    }

    @Test
    void pageAndLimitAreNormalized() {
        when(folderSupport.resolveActor(actorUserId, actorTenantId)).thenReturn(actor);
        when(fileRepository.findAll(
                ArgumentMatchers.<Specification<File>>any(),
                ArgumentMatchers.any(Pageable.class)
        )).thenReturn(new PageImpl<>(List.of()));

        service.execute(query(FolderItemType.FILE, null, null, -1, 500));

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(fileRepository).findAll(
                ArgumentMatchers.<Specification<File>>any(),
                pageableCaptor.capture()
        );
        assertEquals(0, pageableCaptor.getValue().getPageNumber());
        assertEquals(100, pageableCaptor.getValue().getPageSize());
    }

    private ListAllTenantFolderItemsUseCase.ListAllTenantFolderItemsQuery query(
            FolderItemType type,
            FileStatus status,
            UUID parentId,
            int page,
            int limit
    ) {
        return new ListAllTenantFolderItemsUseCase.ListAllTenantFolderItemsQuery(
                actorUserId,
                actorTenantId,
                RoleCode.SUPER_ADMIN,
                null,
                type,
                parentId,
                status,
                null,
                null,
                page,
                limit,
                AdminFolderItemSortBy.CREATED_AT,
                null
        );
    }

    private Tenant tenant(UUID id, String name) {
        Tenant tenant = Tenant.builder()
                .name(name)
                .slug(name.toLowerCase().replace(" ", "-"))
                .isActive(true)
                .build();
        tenant.setId(id);
        tenant.setCreatedAt(Instant.now());
        tenant.setUpdatedAt(Instant.now());
        return tenant;
    }

    private User user(UUID id, Tenant tenant, String fullName) {
        User user = User.builder()
                .tenant(tenant)
                .email(id + "@example.com")
                .passwordHash("hash")
                .fullName(fullName)
                .build();
        user.setId(id);
        user.setCreatedAt(Instant.now());
        user.setUpdatedAt(Instant.now());
        return user;
    }

    private Folder folder(UUID id, Tenant tenant, User creator, String name, Folder parent) {
        Folder folder = Folder.builder()
                .tenant(tenant)
                .parent(parent)
                .owner(null)
                .name(name)
                .createdBy(creator)
                .build();
        folder.setId(id);
        folder.setCreatedAt(Instant.now());
        folder.setUpdatedAt(Instant.now());
        return folder;
    }

    private File file(UUID id, Tenant tenant, User creator, String name, Folder parent) {
        File file = File.builder()
                .tenant(tenant)
                .folder(parent)
                .uploadedBy(creator)
                .originalName(name)
                .storageKey("key")
                .mimeType("application/pdf")
                .sizeBytes(10L)
                .status(FileStatus.ACTIVE)
                .build();
        file.setId(id);
        file.setCreatedAt(Instant.now());
        file.setUpdatedAt(Instant.now());
        return file;
    }
}
