package com.filestorage.application.usecase.file;

import com.filestorage.adapter.out.persistence.entity.File;
import com.filestorage.adapter.out.persistence.entity.Folder;
import com.filestorage.adapter.out.persistence.entity.Tenant;
import com.filestorage.adapter.out.persistence.entity.User;
import com.filestorage.adapter.out.persistence.mapper.FileViewMapper;
import com.filestorage.adapter.out.persistence.repository.FileJpaRepository;
import com.filestorage.adapter.out.persistence.repository.FolderJpaRepository;
import com.filestorage.application.usecase.folder.FolderAccessPolicy;
import com.filestorage.domain.model.enums.FileStatus;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.file.FileView;
import com.filestorage.domain.port.in.file.MoveFileUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MoveFileServiceTest {

    @Mock
    private FileJpaRepository fileRepository;
    @Mock
    private FolderJpaRepository folderRepository;
    @Mock
    private FileSupport fileSupport;
    @Mock
    private FileViewMapper fileViewMapper;
    @Mock
    private FileAccessPolicy fileAccessPolicy;
    @Mock
    private FolderAccessPolicy folderAccessPolicy;

    private MoveFileService service;

    @BeforeEach
    void setUp() {
        service = new MoveFileService(
                fileRepository,
                folderRepository,
                fileSupport,
                fileViewMapper,
                fileAccessPolicy,
                folderAccessPolicy
        );
    }

    @Test
    void moveUpdatesFolder() {
        UUID actorUserId = UUID.randomUUID();
        UUID actorTenantId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        UUID targetFolderId = UUID.randomUUID();

        User actor = actor(actorUserId, actorTenantId);
        File file = file(fileId, actorTenantId, actorUserId, folder(targetFolderId, actorTenantId, actorUserId));
        Folder targetFolder = folder(targetFolderId, actorTenantId, actorUserId);

        when(fileSupport.resolveActor(actorUserId, actorTenantId)).thenReturn(actor);
        when(fileSupport.findReadableFile(fileId, actorTenantId, RoleCode.TENANT_ADMIN)).thenReturn(file);
        when(folderRepository.findByIdAndTenantId(targetFolderId, actorTenantId)).thenReturn(Optional.of(targetFolder));
        when(fileRepository.save(any())).thenReturn(file);
        when(fileViewMapper.toFileView(file)).thenReturn(new FileView(fileId, "doc.pdf", "application/pdf", 1L,
                FileStatus.ACTIVE, targetFolderId, actorTenantId, "key", actorUserId, "Uploader", null, null, false,
                null, null, null, null));

        service.execute(new MoveFileUseCase.MoveFileCommand(
                actorUserId,
                actorTenantId,
                RoleCode.TENANT_ADMIN,
                fileId,
                targetFolderId
        ));

        verify(fileRepository).save(argThat(saved -> saved.getFolder() != null
                && targetFolderId.equals(saved.getFolder().getId())));
    }

    @Test
    void moveToRootClearsFolder() {
        UUID actorUserId = UUID.randomUUID();
        UUID actorTenantId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();

        User actor = actor(actorUserId, actorTenantId);
        Folder currentFolder = folder(UUID.randomUUID(), actorTenantId, actorUserId);
        File file = file(fileId, actorTenantId, actorUserId, currentFolder);

        when(fileSupport.resolveActor(actorUserId, actorTenantId)).thenReturn(actor);
        when(fileSupport.findReadableFile(fileId, actorTenantId, RoleCode.TENANT_ADMIN)).thenReturn(file);
        when(fileRepository.save(any())).thenReturn(file);
        when(fileViewMapper.toFileView(file)).thenReturn(new FileView(fileId, "doc.pdf", "application/pdf", 1L,
                FileStatus.ACTIVE, null, actorTenantId, "key", actorUserId, "Uploader", null, null, false,
                null, null, null, null));

        service.execute(new MoveFileUseCase.MoveFileCommand(
                actorUserId,
                actorTenantId,
                RoleCode.TENANT_ADMIN,
                fileId,
                null
        ));

        verify(fileRepository).save(argThat(saved -> saved.getFolder() == null));
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

    private Folder folder(UUID folderId, UUID tenantId, UUID createdById) {
        Tenant tenant = Tenant.builder().name("Tenant").slug("tenant").isActive(true).build();
        tenant.setId(tenantId);

        User creator = User.builder()
                .tenant(tenant)
                .email("creator@example.com")
                .passwordHash("hash")
                .fullName("Creator")
                .build();
        creator.setId(createdById);

        Folder folder = Folder.builder()
                .tenant(tenant)
                .name("Folder")
                .createdBy(creator)
                .build();
        folder.setId(folderId);
        return folder;
    }

    private File file(UUID fileId, UUID tenantId, UUID uploadedById, Folder folder) {
        Tenant tenant = Tenant.builder().name("Tenant").slug("tenant").isActive(true).build();
        tenant.setId(tenantId);

        User uploader = User.builder()
                .tenant(tenant)
                .email("uploader@example.com")
                .passwordHash("hash")
                .fullName("Uploader")
                .build();
        uploader.setId(uploadedById);

        File file = File.builder()
                .tenant(tenant)
                .uploadedBy(uploader)
                .folder(folder)
                .originalName("doc.pdf")
                .storageKey("storage")
                .sizeBytes(1L)
                .status(FileStatus.ACTIVE)
                .isIndexed(false)
                .build();
        file.setId(fileId);
        return file;
    }
}
