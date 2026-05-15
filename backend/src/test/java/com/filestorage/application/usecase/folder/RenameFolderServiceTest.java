package com.filestorage.application.usecase.folder;

import com.filestorage.adapter.out.persistence.entity.Folder;
import com.filestorage.adapter.out.persistence.entity.Tenant;
import com.filestorage.adapter.out.persistence.entity.User;
import com.filestorage.adapter.out.persistence.repository.FolderJpaRepository;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.port.in.folder.RenameFolderUseCase;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RenameFolderServiceTest {

    @Mock
    private FolderJpaRepository folderRepository;
    @Mock
    private FolderSupport folderSupport;
    @Mock
    private FolderAccessPolicy folderAccessPolicy;

    private RenameFolderService service;

    @BeforeEach
    void setUp() {
        service = new RenameFolderService(folderRepository, folderSupport, folderAccessPolicy);
    }

    @Test
    void renameRejectsNameConflict() {
        UUID actorUserId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID folderId = UUID.randomUUID();

        User actor = actor(actorUserId, tenantId);
        Folder folder = folder(folderId, actor, "Old");

        when(folderSupport.resolveActor(actorUserId, tenantId)).thenReturn(actor);
        when(folderSupport.findReadableFolder(folderId, tenantId, RoleCode.TENANT_ADMIN)).thenReturn(folder);
        when(folderRepository.existsByTenantIdAndParentIdAndName(tenantId, null, "New"))
                .thenReturn(true);

        var command = new RenameFolderUseCase.RenameFolderCommand(
                actorUserId,
                tenantId,
                RoleCode.TENANT_ADMIN,
                folderId,
                "New"
        );

        AppException ex = assertThrows(AppException.class, () -> service.execute(command));
        assertEquals(ErrorCode.FOLDER_NAME_CONFLICT, ex.getError());
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

    private Folder folder(UUID id, User actor, String name) {
        Folder folder = Folder.builder()
                .tenant(actor.getTenant())
                .parent(null)
                .owner(null)
                .name(name)
                .createdBy(actor)
                .build();
        folder.setId(id);
        return folder;
    }
}
