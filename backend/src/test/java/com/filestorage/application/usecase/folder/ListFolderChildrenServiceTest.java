package com.filestorage.application.usecase.folder;

import com.filestorage.adapter.out.persistence.entity.File;
import com.filestorage.adapter.out.persistence.entity.Folder;
import com.filestorage.adapter.out.persistence.entity.Tenant;
import com.filestorage.adapter.out.persistence.entity.User;
import com.filestorage.adapter.out.persistence.repository.FileJpaRepository;
import com.filestorage.adapter.out.persistence.repository.FolderJpaRepository;
import com.filestorage.application.usecase.share.ShareResolutionService;
import com.filestorage.domain.model.enums.FolderItemType;
import com.filestorage.domain.model.enums.MyPermission;
import com.filestorage.domain.model.enums.PermissionSource;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.folder.FolderItemPageView;
import com.filestorage.domain.model.share.PermissionResult;
import com.filestorage.domain.port.in.folder.ListFolderChildrenUseCase;
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

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ListFolderChildrenServiceTest {

    @Mock
    private FolderJpaRepository folderRepository;
    @Mock
    private FileJpaRepository fileRepository;
    @Mock
    private FolderSupport folderSupport;
    @Mock
    private FolderAccessPolicy folderAccessPolicy;
    @Mock
    private ShareResolutionService shareResolutionService;

    private ListFolderChildrenService service;

    @BeforeEach
    void setUp() {
        service = new ListFolderChildrenService(
                folderRepository,
                fileRepository,
                folderSupport,
                folderAccessPolicy,
                shareResolutionService
        );
    }

    @Test
    void foldersAreListedBeforeFiles() {
        UUID actorUserId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();

        User actor = actor(actorUserId, tenantId);
        Folder parent = folder(parentId, tenantId, actor, "Parent");

        when(folderSupport.resolveActor(actorUserId, tenantId)).thenReturn(actor);
        when(folderSupport.findReadableFolder(parentId, tenantId, RoleCode.TENANT_ADMIN)).thenReturn(parent);

        Folder folderA = folder(UUID.randomUUID(), tenantId, actor, "Design");
        Folder folderB = folder(UUID.randomUUID(), tenantId, actor, "Docs");
        File file = file(UUID.randomUUID(), tenantId, actor, "readme.pdf");
        PermissionResult ownerPermission = new PermissionResult(MyPermission.OWNER, PermissionSource.OWNERSHIP);

        when(shareResolutionService.resolveFolder(actorUserId, tenantId, RoleCode.TENANT_ADMIN, folderA))
                .thenReturn(ownerPermission);
        when(shareResolutionService.resolveFolder(actorUserId, tenantId, RoleCode.TENANT_ADMIN, folderB))
                .thenReturn(ownerPermission);
        when(shareResolutionService.resolveFile(actorUserId, tenantId, RoleCode.TENANT_ADMIN, file))
                .thenReturn(ownerPermission);

        when(folderRepository.count(ArgumentMatchers.<Specification<Folder>>any())).thenReturn(2L);
        when(fileRepository.count(ArgumentMatchers.<Specification<File>>any())).thenReturn(1L);

        when(folderRepository.findAll(
                ArgumentMatchers.<Specification<Folder>>any(),
                ArgumentMatchers.any(org.springframework.data.domain.Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(folderA, folderB)));

        when(fileRepository.findAll(
                ArgumentMatchers.<Specification<File>>any(),
                ArgumentMatchers.any(org.springframework.data.domain.Pageable.class)
        )).thenReturn(new PageImpl<>(List.of(file)));

        var query = new ListFolderChildrenUseCase.ListFolderChildrenQuery(
                actorUserId,
                tenantId,
                RoleCode.TENANT_ADMIN,
                parentId,
                1,
                10,
                null,
                null,
                null,
                null,
                null
        );

        FolderItemPageView result = service.execute(query);

        assertEquals(3, result.total());
        assertEquals(FolderItemType.FOLDER, result.data().get(0).type());
        assertEquals(FolderItemType.FOLDER, result.data().get(1).type());
        assertEquals(FolderItemType.FILE, result.data().get(2).type());
        assertEquals(MyPermission.OWNER, result.data().get(0).myPermission());
        assertEquals(PermissionSource.OWNERSHIP, result.data().get(0).permissionSource());
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

    private Folder folder(UUID id, UUID tenantId, User actor, String name) {
        Folder folder = Folder.builder()
                .tenant(actor.getTenant())
                .parent(null)
                .owner(null)
                .name(name)
                .createdBy(actor)
                .build();
        folder.setId(id);
        folder.setCreatedAt(Instant.now());
        folder.setUpdatedAt(Instant.now());
        return folder;
    }

    private File file(UUID id, UUID tenantId, User actor, String name) {
        File file = File.builder()
                .tenant(actor.getTenant())
                .uploadedBy(actor)
                .originalName(name)
                .storageKey("key")
                .mimeType("application/pdf")
                .sizeBytes(10L)
                .build();
        file.setId(id);
        file.setCreatedAt(Instant.now());
        file.setUpdatedAt(Instant.now());
        return file;
    }
}
