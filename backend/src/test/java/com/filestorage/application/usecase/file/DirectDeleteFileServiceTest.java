package com.filestorage.application.usecase.file;

import com.filestorage.adapter.out.persistence.entity.File;
import com.filestorage.adapter.out.persistence.entity.Tenant;
import com.filestorage.adapter.out.persistence.entity.User;
import com.filestorage.adapter.out.persistence.mapper.FileViewMapper;
import com.filestorage.adapter.out.persistence.repository.FileJpaRepository;
import com.filestorage.adapter.out.persistence.repository.FileShareJpaRepository;
import com.filestorage.adapter.out.persistence.repository.UserJpaRepository;
import com.filestorage.domain.model.enums.FileStatus;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.file.FileView;
import com.filestorage.domain.port.in.file.DirectDeleteFileUseCase;
import com.filestorage.domain.port.out.ObjectStoragePort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DirectDeleteFileServiceTest {

    @Mock
    private FileJpaRepository fileRepository;
    @Mock
    private FileShareJpaRepository fileShareRepository;
    @Mock
    private UserJpaRepository userRepository;
    @Mock
    private FileSupport fileSupport;
    @Mock
    private ObjectStoragePort objectStoragePort;
    @Mock
    private FileViewMapper fileViewMapper;

    private DirectDeleteFileService directDeleteFileService;

    @BeforeEach
    void setUp() {
        directDeleteFileService = new DirectDeleteFileService(
                fileRepository,
                fileShareRepository,
                userRepository,
                fileSupport,
                objectStoragePort,
                fileViewMapper
        );
    }

    @Test
    void deleteActiveUploaderDecrementsOnlyUserUsage() {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        User actor = buildActor(actorId, tenantId);
        File file = buildFile(fileId, tenantId, true, 60L, 60L, 25L);
        when(fileSupport.resolveActor(actorId, tenantId)).thenReturn(actor);
        when(fileSupport.findReadableFile(fileId, tenantId, RoleCode.TENANT_ADMIN)).thenReturn(file);
        when(fileRepository.save(any(File.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(fileViewMapper.toFileView(any(File.class))).thenAnswer(invocation -> toView(invocation.getArgument(0)));

        var result = directDeleteFileService.execute(new DirectDeleteFileUseCase.DirectDeleteFileCommand(
                actorId, tenantId, RoleCode.TENANT_ADMIN, fileId
        ));

        assertEquals(FileStatus.DELETED, result.status());
        assertEquals(35L, file.getUploadedBy().getStorageUsedBytes());
        assertEquals(60L, file.getTenant().getStorageUsedBytes());
        assertEquals(FileStatus.DELETED, file.getStatus());
        assertNotNull(file.getDeletedAt());
        verify(userRepository).save(file.getUploadedBy());
        verify(fileShareRepository).deleteByFileId(fileId);
        verify(fileRepository).save(file);
        verify(fileRepository, never()).delete(file);
        verify(objectStoragePort).delete(file.getStorageKey());
    }

    @Test
    void deleteInactiveUploaderStillDecrementsOnlyUserUsage() {
        UUID tenantId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        User actor = buildActor(actorId, tenantId);
        File file = buildFile(fileId, tenantId, false, 60L, 60L, 25L);
        when(fileSupport.resolveActor(actorId, tenantId)).thenReturn(actor);
        when(fileSupport.findReadableFile(fileId, tenantId, RoleCode.TENANT_ADMIN)).thenReturn(file);
        when(fileRepository.save(any(File.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(fileViewMapper.toFileView(any(File.class))).thenAnswer(invocation -> toView(invocation.getArgument(0)));

        var result = directDeleteFileService.execute(new DirectDeleteFileUseCase.DirectDeleteFileCommand(
                actorId, tenantId, RoleCode.TENANT_ADMIN, fileId
        ));

        assertEquals(FileStatus.DELETED, result.status());
        assertEquals(35L, file.getUploadedBy().getStorageUsedBytes());
        assertEquals(60L, file.getTenant().getStorageUsedBytes());
        assertEquals(FileStatus.DELETED, file.getStatus());
        assertNotNull(file.getDeletedAt());
        verify(userRepository).save(file.getUploadedBy());
        verify(fileShareRepository).deleteByFileId(fileId);
        verify(fileRepository).save(file);
        verify(fileRepository, never()).delete(file);
        verify(objectStoragePort).delete(file.getStorageKey());
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
