package com.filestorage.application.usecase.file;

import com.filestorage.adapter.out.persistence.entity.File;
import com.filestorage.adapter.out.persistence.entity.Tenant;
import com.filestorage.adapter.out.persistence.entity.User;
import com.filestorage.adapter.out.persistence.mapper.FileViewMapper;
import com.filestorage.adapter.out.persistence.repository.FileJpaRepository;
import com.filestorage.application.usecase.indexing.AfterCommitIndexingPublisher;
import com.filestorage.domain.model.enums.FileStatus;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.file.FileView;
import com.filestorage.domain.port.in.file.RenameFileUseCase;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RenameFileServiceTest {

    @Mock
    private FileJpaRepository fileRepository;
    @Mock
    private FileSupport fileSupport;
    @Mock
    private FileViewMapper fileViewMapper;
    @Mock
    private FileAccessPolicy fileAccessPolicy;
    @Mock
    private AfterCommitIndexingPublisher indexingPublisher;

    private RenameFileService service;

    @BeforeEach
    void setUp() {
        service = new RenameFileService(fileRepository, fileSupport, fileViewMapper, fileAccessPolicy, indexingPublisher);
    }

    @Test
    void renameUpdatesOriginalName() {
        UUID actorUserId = UUID.randomUUID();
        UUID actorTenantId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        File file = file(fileId, actorTenantId, actorUserId);

        when(fileSupport.resolveActor(actorUserId, actorTenantId)).thenReturn(actor(actorUserId, actorTenantId));
        when(fileSupport.findReadableFile(fileId, actorTenantId, RoleCode.TENANT_ADMIN)).thenReturn(file);
        when(fileRepository.save(any())).thenReturn(file);
        when(fileViewMapper.toFileView(file)).thenReturn(new FileView(fileId, "renamed.pdf", "application/pdf", 3L,
                FileStatus.ACTIVE, null, actorTenantId, "key", actorUserId, "Uploader", null, null, false, null,
                null, null, null));

        service.execute(new RenameFileUseCase.RenameFileCommand(
                actorUserId,
                actorTenantId,
                RoleCode.TENANT_ADMIN,
                fileId,
                "  renamed.pdf  "
        ));

        verify(fileRepository).save(argThat(saved -> "renamed.pdf".equals(saved.getOriginalName())));
    }

    @Test
    void renameRejectsBlankName() {
        UUID actorUserId = UUID.randomUUID();
        UUID actorTenantId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();

        when(fileSupport.resolveActor(actorUserId, actorTenantId)).thenReturn(actor(actorUserId, actorTenantId));
        when(fileSupport.findReadableFile(fileId, actorTenantId, RoleCode.TENANT_ADMIN))
                .thenReturn(file(fileId, actorTenantId, actorUserId));

        AppException ex = assertThrows(AppException.class, () -> service.execute(new RenameFileUseCase.RenameFileCommand(
                actorUserId,
                actorTenantId,
                RoleCode.TENANT_ADMIN,
                fileId,
                "   "
        )));

        assertEquals(ErrorCode.INVALID_PAYLOAD, ex.getError());
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

    private File file(UUID fileId, UUID tenantId, UUID uploadedById) {
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
                .originalName("a.pdf")
                .storageKey("storage")
                .sizeBytes(10L)
                .status(FileStatus.ACTIVE)
                .isIndexed(false)
                .build();
        file.setId(fileId);
        return file;
    }
}
