package com.filestorage.application.usecase.file;

import com.filestorage.adapter.out.persistence.entity.File;
import com.filestorage.adapter.out.persistence.entity.Tenant;
import com.filestorage.adapter.out.persistence.entity.User;
import com.filestorage.adapter.out.persistence.mapper.FileViewMapper;
import com.filestorage.domain.model.enums.FileStatus;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.file.FileView;
import com.filestorage.domain.port.in.file.DirectDeleteFileUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DirectDeleteFileServiceTest {

    @Mock
    private FileSupport fileSupport;
    @Mock
    private FileHardDeleteSupport hardDeleteSupport;
    @Mock
    private FileAccessPolicy fileAccessPolicy;
    @Mock
    private FileViewMapper fileViewMapper;

    private DirectDeleteFileService directDeleteFileService;

    @BeforeEach
    void setUp() {
        directDeleteFileService = new DirectDeleteFileService(
                fileSupport,
                hardDeleteSupport,
                fileAccessPolicy,
                fileViewMapper
        );
    }

    @Test
    void deleteDelegatesPolicyCheckAndHardDelete() {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        User actor = buildActor(actorId, tenantId);
        File file = buildFile(fileId, tenantId, true, 60L, 60L, 25L);
        when(fileSupport.resolveActor(actorId, tenantId)).thenReturn(actor);
        when(fileSupport.findReadableFile(fileId, tenantId, RoleCode.TENANT_ADMIN)).thenReturn(file);
        when(fileViewMapper.toFileView(any(File.class))).thenAnswer(invocation -> toView(invocation.getArgument(0)));

        var result = directDeleteFileService.execute(new DirectDeleteFileUseCase.DirectDeleteFileCommand(
                actorId, tenantId, RoleCode.TENANT_ADMIN, fileId
        ));

        assertEquals(FileStatus.ACTIVE, result.status());
        verify(fileAccessPolicy).requireCanModify(file, actorId, tenantId, RoleCode.TENANT_ADMIN);
        verify(hardDeleteSupport).hardDelete(file);
    }

    @Test
    void deleteStillRunsForInactiveUploader() {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        User actor = buildActor(actorId, tenantId);
        File file = buildFile(fileId, tenantId, false, 60L, 60L, 25L);
        when(fileSupport.resolveActor(actorId, tenantId)).thenReturn(actor);
        when(fileSupport.findReadableFile(fileId, tenantId, RoleCode.TENANT_ADMIN)).thenReturn(file);
        when(fileViewMapper.toFileView(any(File.class))).thenAnswer(invocation -> toView(invocation.getArgument(0)));

        var result = directDeleteFileService.execute(new DirectDeleteFileUseCase.DirectDeleteFileCommand(
                actorId, tenantId, RoleCode.TENANT_ADMIN, fileId
        ));

        assertEquals(FileStatus.ACTIVE, result.status());
        verify(fileAccessPolicy).requireCanModify(file, actorId, tenantId, RoleCode.TENANT_ADMIN);
        verify(hardDeleteSupport).hardDelete(file);
    }

    private User buildActor(UUID actorId, UUID tenantId) {
        Tenant tenant = Tenant.builder().name("Tenant").slug("tenant").isActive(true).build();
        tenant.setId(tenantId);
        User actor = User.builder()
                .tenant(tenant)
                .email("actor@example.com")
                .passwordHash("hash")
                .fullName("Actor")
                .isActive(true)
                .build();
        actor.setId(actorId);
        return actor;
    }

    private File buildFile(UUID fileId, UUID tenantId, boolean uploaderActive, long uploaderUsed, long tenantUsed, long fileSize) {
        Tenant tenant = Tenant.builder().name("Tenant").slug("tenant").isActive(true).build();
        tenant.setId(tenantId);
        tenant.setStorageUsedBytes(tenantUsed);
        User uploader = User.builder()
                .tenant(tenant)
                .email("uploader@example.com")
                .passwordHash("hash")
                .fullName("Uploader")
                .isActive(uploaderActive)
                .storageUsedBytes(uploaderUsed)
                .build();
        uploader.setId(UUID.randomUUID());
        File file = File.builder()
                .tenant(tenant)
                .uploadedBy(uploader)
                .originalName("f.txt")
                .storageKey("tenant/uploader/file")
                .mimeType("text/plain")
                .sizeBytes(fileSize)
                .status(FileStatus.ACTIVE)
                .isIndexed(false)
                .build();
        file.setId(fileId);
        return file;
    }

    private FileView toView(File file) {
        return new FileView(
                file.getId(),
                file.getOriginalName(),
                file.getMimeType(),
                file.getSizeBytes(),
                file.getStatus(),
                null,
                file.getTenant().getId(),
                file.getStorageKey(),
                file.getUploadedBy().getId(),
                file.getUploadedBy().getFullName(),
                file.getChecksumMd5(),
                file.getThumbnailKey(),
                file.isIndexed(),
                file.getDeletedAt(),
                file.getScheduledDeleteAt(),
                file.getCreatedAt(),
                file.getUpdatedAt()
        );
    }
}
