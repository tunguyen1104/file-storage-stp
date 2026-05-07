package com.filestorage.application.usecase.file;

import com.filestorage.adapter.out.persistence.entity.File;
import com.filestorage.adapter.out.persistence.entity.Tenant;
import com.filestorage.adapter.out.persistence.entity.User;
import com.filestorage.adapter.out.persistence.mapper.FileViewMapper;
import com.filestorage.adapter.out.persistence.repository.FileJpaRepository;
import com.filestorage.adapter.out.persistence.repository.FolderJpaRepository;
import com.filestorage.adapter.out.persistence.repository.UserJpaRepository;
import com.filestorage.domain.model.enums.FileStatus;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.file.FileView;
import com.filestorage.domain.port.in.file.UploadFileUseCase;
import com.filestorage.domain.port.out.ObjectStoragePort;
import com.filestorage.infrastructure.config.StorageProperties;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UploadFileServiceTest {

    @Mock
    private FileJpaRepository fileRepository;
    @Mock
    private FolderJpaRepository folderRepository;
    @Mock
    private UserJpaRepository userRepository;
    @Mock
    private FileSupport fileSupport;
    @Mock
    private FileViewMapper fileViewMapper;
    @Mock
    private ObjectStoragePort objectStoragePort;

    private UploadFileService uploadFileService;

    @BeforeEach
    void setUp() {
        uploadFileService = new UploadFileService(
                fileRepository,
                folderRepository,
                userRepository,
                fileSupport,
                fileViewMapper,
                objectStoragePort,
                new StorageProperties()
        );
    }

    @Test
    void successfulUploadIncrementsUsageAtomically() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        User actor = actor(userId, tenantId, 10L, 100L);

        when(fileSupport.resolveActor(userId, tenantId)).thenReturn(actor);
        when(objectStoragePort.buildStorageKey(anyString(), anyString(), anyString(), anyString())).thenReturn("tenant/user/file.txt");
        when(fileRepository.save(any(File.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));
        when(userRepository.incrementStorageUsedIfQuotaAllows(userId, 5L)).thenReturn(1);
        when(fileViewMapper.toFileView(any(File.class))).thenAnswer(invocation -> toView(invocation.getArgument(0)));

        FileView view = uploadFileService.execute(new UploadFileUseCase.UploadFileCommand(
                userId, tenantId, RoleCode.MEMBER, null, "file.txt", "text/plain", "hello".getBytes()
        ));

        assertEquals(FileStatus.ACTIVE, view.status());
        verify(userRepository).incrementStorageUsedIfQuotaAllows(userId, 5L);
        verify(userRepository, never()).save(any(User.class));
    }

    @Test
    void uploadFailsWhenAtomicQuotaIncrementRejects() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        User actor = actor(userId, tenantId, 10L, 100L);

        when(fileSupport.resolveActor(userId, tenantId)).thenReturn(actor);
        when(objectStoragePort.buildStorageKey(anyString(), anyString(), anyString(), anyString())).thenReturn("tenant/user/file.txt");
        when(fileRepository.save(any(File.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));
        when(userRepository.incrementStorageUsedIfQuotaAllows(userId, 5L)).thenReturn(0);

        AppException ex = assertThrows(AppException.class, () -> uploadFileService.execute(
                new UploadFileUseCase.UploadFileCommand(
                        userId, tenantId, RoleCode.MEMBER, null, "file.txt", "text/plain", "hello".getBytes()
                )
        ));

        assertEquals(ErrorCode.USER_QUOTA_EXCEEDED, ex.getError());
        verify(objectStoragePort).deleteObjectIfExists("tenant/user/file.txt");

        ArgumentCaptor<File> savedFiles = ArgumentCaptor.forClass(File.class);
        verify(fileRepository, atLeastOnce()).save(savedFiles.capture());
        File lastSaved = savedFiles.getAllValues().get(savedFiles.getAllValues().size() - 1);
        assertEquals(FileStatus.UPLOAD_FAILED, lastSaved.getStatus());
    }

    private User actor(UUID userId, UUID tenantId, long used, long quota) {
        Tenant tenant = Tenant.builder()
                .name("Tenant")
                .slug("tenant")
                .isActive(true)
                .build();
        tenant.setId(tenantId);

        User user = User.builder()
                .tenant(tenant)
                .email("u@example.com")
                .passwordHash("hash")
                .fullName("User")
                .isActive(true)
                .storageUsedBytes(used)
                .storageQuotaBytes(quota)
                .build();
        user.setId(userId);
        return user;
    }

    private File withId(File file) {
        if (file.getId() == null) {
            file.setId(UUID.randomUUID());
        }
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
