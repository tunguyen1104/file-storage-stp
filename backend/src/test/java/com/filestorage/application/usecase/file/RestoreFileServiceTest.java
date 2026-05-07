package com.filestorage.application.usecase.file;

import com.filestorage.adapter.out.persistence.entity.File;
import com.filestorage.adapter.out.persistence.entity.Folder;
import com.filestorage.adapter.out.persistence.entity.Tenant;
import com.filestorage.adapter.out.persistence.entity.User;
import com.filestorage.adapter.out.persistence.mapper.FileViewMapper;
import com.filestorage.adapter.out.persistence.repository.FileJpaRepository;
import com.filestorage.adapter.out.persistence.repository.FolderJpaRepository;
import com.filestorage.domain.model.enums.FileStatus;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.file.FileView;
import com.filestorage.domain.port.in.file.RestoreFileUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RestoreFileServiceTest {

    @Mock
    private FileJpaRepository fileRepository;
    @Mock
    private FolderJpaRepository folderRepository;
    @Mock
    private FileSupport fileSupport;
    @Mock
    private FileViewMapper fileViewMapper;

    private RestoreFileService service;

    @BeforeEach
    void setUp() {
        service = new RestoreFileService(fileRepository, folderRepository, fileSupport, fileViewMapper);
    }

    @Test
    void restoreMovesFileToRootWhenOriginalFolderWasDeleted() {
        UUID actorUserId = UUID.randomUUID();
        UUID actorTenantId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        UUID folderId = UUID.randomUUID();

        User actor = actor(actorUserId, actorTenantId);
        File file = file(fileId, actorTenantId, actorUserId, folder(folderId, actor));

        when(fileSupport.resolveActor(actorUserId, actorTenantId)).thenReturn(actor);
        when(fileSupport.findReadableFile(fileId, actorTenantId, RoleCode.TENANT_ADMIN)).thenReturn(file);
        when(folderRepository.existsByIdAndTenantId(folderId, actorTenantId)).thenReturn(false);
        when(fileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(fileViewMapper.toFileView(any())).thenReturn(new FileView(fileId, "doc.pdf", "application/pdf", 1L,
                FileStatus.ACTIVE, null, actorTenantId, "key", actorUserId, "Uploader", null, null, false,
                null, null, null, null));

        service.execute(new RestoreFileUseCase.RestoreFileCommand(
                actorUserId,
                actorTenantId,
                RoleCode.TENANT_ADMIN,
                fileId
        ));

        verify(fileRepository).save(argThat(saved -> saved.getStatus() == FileStatus.ACTIVE
                && saved.getFolder() == null
                && saved.getDeletedAt() == null
                && saved.getScheduledDeleteAt() == null));
    }

    @Test
    void restoreKeepsFolderWhenOriginalFolderStillExists() {
        UUID actorUserId = UUID.randomUUID();
        UUID actorTenantId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        UUID folderId = UUID.randomUUID();

        User actor = actor(actorUserId, actorTenantId);
        File file = file(fileId, actorTenantId, actorUserId, folder(folderId, actor));

        when(fileSupport.resolveActor(actorUserId, actorTenantId)).thenReturn(actor);
        when(fileSupport.findReadableFile(fileId, actorTenantId, RoleCode.TENANT_ADMIN)).thenReturn(file);
        when(folderRepository.existsByIdAndTenantId(folderId, actorTenantId)).thenReturn(true);
        when(fileRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(fileViewMapper.toFileView(any())).thenReturn(new FileView(fileId, "doc.pdf", "application/pdf", 1L,
                FileStatus.ACTIVE, folderId, actorTenantId, "key", actorUserId, "Uploader", null, null, false,
                null, null, null, null));

        service.execute(new RestoreFileUseCase.RestoreFileCommand(
                actorUserId,
                actorTenantId,
                RoleCode.TENANT_ADMIN,
                fileId
        ));

        verify(fileRepository).save(argThat(saved -> saved.getStatus() == FileStatus.ACTIVE
                && saved.getFolder() != null
                && folderId.equals(saved.getFolder().getId())
                && saved.getDeletedAt() == null
                && saved.getScheduledDeleteAt() == null));
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

    private Folder folder(UUID folderId, User creator) {
        Folder folder = Folder.builder()
                .tenant(creator.getTenant())
                .name("Folder")
                .createdBy(creator)
                .build();
        folder.setId(folderId);
        return folder;
    }

    private File file(UUID fileId, UUID tenantId, UUID uploadedById, Folder folder) {
        Tenant tenant = folder.getTenant();

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
                .mimeType("application/pdf")
                .sizeBytes(1L)
                .status(FileStatus.PENDING_DELETE)
                .deletedAt(Instant.now())
                .scheduledDeleteAt(Instant.now().plusSeconds(3600))
                .isIndexed(false)
                .build();
        file.setId(fileId);
        return file;
    }
}
