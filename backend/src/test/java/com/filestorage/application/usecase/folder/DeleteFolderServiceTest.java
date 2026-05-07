package com.filestorage.application.usecase.folder;

import com.filestorage.adapter.out.persistence.entity.Folder;
import com.filestorage.adapter.out.persistence.entity.Tenant;
import com.filestorage.adapter.out.persistence.entity.User;
import com.filestorage.adapter.out.persistence.repository.FileJpaRepository;
import com.filestorage.adapter.out.persistence.repository.FolderJpaRepository;
import com.filestorage.domain.model.enums.FileStatus;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.port.in.folder.DeleteFolderUseCase;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeleteFolderServiceTest {

    private static final List<FileStatus> DELETABLE_FILE_STATUSES = List.of(
            FileStatus.PENDING_DELETE,
            FileStatus.DELETED
    );

    @Mock
    private FolderJpaRepository folderRepository;
    @Mock
    private FileJpaRepository fileRepository;
    @Mock
    private FolderSupport folderSupport;

    private DeleteFolderService service;

    @BeforeEach
    void setUp() {
        service = new DeleteFolderService(folderRepository, fileRepository, folderSupport);
    }

    @Test
    void deleteClearsPendingDeleteAndDeletedFileReferencesBeforeDeletingFolder() {
        UUID actorUserId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID folderId = UUID.randomUUID();

        User actor = actor(actorUserId, tenantId);
        Folder folder = folder(folderId, actor);

        when(folderSupport.resolveActor(actorUserId, tenantId)).thenReturn(actor);
        when(folderSupport.findReadableFolder(folderId, tenantId, RoleCode.TENANT_ADMIN)).thenReturn(folder);
        when(folderRepository.existsByTenantIdAndParentId(tenantId, folderId)).thenReturn(false);
        when(fileRepository.existsByFolderIdAndStatusNotIn(folderId, DELETABLE_FILE_STATUSES)).thenReturn(false);

        service.execute(new DeleteFolderUseCase.DeleteFolderCommand(
                actorUserId,
                tenantId,
                RoleCode.TENANT_ADMIN,
                folderId
        ));

        InOrder inOrder = inOrder(fileRepository, folderRepository);
        inOrder.verify(fileRepository).clearFolderByFolderIdAndStatusIn(folderId, DELETABLE_FILE_STATUSES);
        inOrder.verify(folderRepository).delete(folder);
    }

    @Test
    void deleteRejectsFolderWhenItContainsAnyNonDeletedFileStatus() {
        UUID actorUserId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID folderId = UUID.randomUUID();

        User actor = actor(actorUserId, tenantId);
        Folder folder = folder(folderId, actor);

        when(folderSupport.resolveActor(actorUserId, tenantId)).thenReturn(actor);
        when(folderSupport.findReadableFolder(folderId, tenantId, RoleCode.TENANT_ADMIN)).thenReturn(folder);
        when(folderRepository.existsByTenantIdAndParentId(tenantId, folderId)).thenReturn(false);
        when(fileRepository.existsByFolderIdAndStatusNotIn(folderId, DELETABLE_FILE_STATUSES)).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> service.execute(new DeleteFolderUseCase.DeleteFolderCommand(
                actorUserId,
                tenantId,
                RoleCode.TENANT_ADMIN,
                folderId
        )));

        assertEquals(ErrorCode.FOLDER_NOT_EMPTY, ex.getError());
        verify(fileRepository, never()).clearFolderByFolderIdAndStatusIn(folderId, DELETABLE_FILE_STATUSES);
        verify(folderRepository, never()).delete(folder);
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

    private Folder folder(UUID id, User actor) {
        Folder folder = Folder.builder()
                .tenant(actor.getTenant())
                .parent(null)
                .owner(null)
                .name("Folder")
                .createdBy(actor)
                .build();
        folder.setId(id);
        return folder;
    }
}
