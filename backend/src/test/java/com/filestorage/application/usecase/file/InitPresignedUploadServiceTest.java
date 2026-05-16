package com.filestorage.application.usecase.file;

import com.filestorage.adapter.out.persistence.entity.File;
import com.filestorage.adapter.out.persistence.entity.Tenant;
import com.filestorage.adapter.out.persistence.entity.User;
import com.filestorage.adapter.out.persistence.repository.FileJpaRepository;
import com.filestorage.adapter.out.persistence.repository.FolderJpaRepository;
import com.filestorage.adapter.out.persistence.repository.UserJpaRepository;
import com.filestorage.domain.model.enums.FileStatus;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.file.PresignedUploadView;
import com.filestorage.domain.port.in.file.InitPresignedUploadUseCase;
import com.filestorage.domain.port.out.ObjectStoragePort;
import com.filestorage.infrastructure.config.UploadProperties;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InitPresignedUploadServiceTest {

    @Mock
    private FileJpaRepository fileRepository;
    @Mock
    private FolderJpaRepository folderRepository;
    @Mock
    private UserJpaRepository userRepository;
    @Mock
    private ObjectStoragePort objectStoragePort;

    private InitPresignedUploadService service;

    @BeforeEach
    void setUp() {
        service = new InitPresignedUploadService(
                fileRepository, folderRepository, userRepository,
                objectStoragePort, new UploadProperties()
        );
    }

    @Test
    void successfulInitReturnsPresignedView() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        User actor = actor(userId, tenantId, 0L, 10_737_418_240L);

        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(actor));
        when(fileRepository.sumSizeBytesByUserAndStatuses(any(), any())).thenReturn(0L);
        when(objectStoragePort.buildStorageKey(anyString(), anyString(), anyString(), anyString()))
                .thenReturn("key/path/file.pdf");
        when(objectStoragePort.generatePresignedPutUrl(anyString(), anyString(), anyInt()))
                .thenReturn("https://minio.example.com/presigned-put");
        when(fileRepository.save(any())).thenReturn(buildFile(UUID.randomUUID(), tenantId, userId));

        var command = new InitPresignedUploadUseCase.InitPresignedUploadCommand(
                userId, tenantId, RoleCode.MEMBER, null, "report.pdf", "application/pdf", 1024L
        );

        PresignedUploadView view = service.execute(command);

        assertNotNull(view.fileId());
        assertEquals("https://minio.example.com/presigned-put", view.uploadUrl());
        assertNotNull(view.expiresAt());
    }

    /**
     * Requests declaring a file larger than the configured hard ceiling (default 4 GiB)
     * are rejected immediately — before any DB or storage I/O.
     */
    @Test
    void rejectsFileTooLarge() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        long overLimit = new UploadProperties().getMaxFileSizeBytes() + 1;
        var command = new InitPresignedUploadUseCase.InitPresignedUploadCommand(
                userId, tenantId, RoleCode.MEMBER, null, "huge.bin", "application/octet-stream", overLimit
        );

        AppException ex = assertThrows(AppException.class, () -> service.execute(command));
        assertEquals(ErrorCode.FILE_TOO_LARGE, ex.getError());
    }

    @Test
    void rejectsWhenUserQuotaExceeded() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        // User has 900 bytes used, quota is 1_000 bytes; uploading 200 bytes exceeds quota
        User actor = actor(userId, tenantId, 900L, 1_000L);

        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(actor));
        when(fileRepository.sumSizeBytesByUserAndStatuses(any(), any())).thenReturn(0L);

        var command = new InitPresignedUploadUseCase.InitPresignedUploadCommand(
                userId, tenantId, RoleCode.MEMBER, null, "file.pdf", "application/pdf", 200L
        );

        AppException ex = assertThrows(AppException.class, () -> service.execute(command));
        assertEquals(ErrorCode.QUOTA_EXCEEDED, ex.getError());
    }

    /**
     * In-flight bytes (PENDING/SCANNING files) count toward effective usage to prevent
     * concurrent uploads from collectively exceeding quota.
     */
    @Test
    void rejectsWhenInFlightBytesExceedQuota() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        // 0 committed, quota 1000; but 800 bytes already in-flight; uploading 300 would exceed
        User actor = actor(userId, tenantId, 0L, 1_000L);

        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.of(actor));
        when(fileRepository.sumSizeBytesByUserAndStatuses(any(), any())).thenReturn(800L);

        var command = new InitPresignedUploadUseCase.InitPresignedUploadCommand(
                userId, tenantId, RoleCode.MEMBER, null, "file.pdf", "application/pdf", 300L
        );

        AppException ex = assertThrows(AppException.class, () -> service.execute(command));
        assertEquals(ErrorCode.QUOTA_EXCEEDED, ex.getError());
    }

    @Test
    void rejectsWhenUserNotFound() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        when(userRepository.findByIdForUpdate(userId)).thenReturn(Optional.empty());

        var command = new InitPresignedUploadUseCase.InitPresignedUploadCommand(
                userId, tenantId, RoleCode.MEMBER, null, "file.pdf", "application/pdf", 1024L
        );

        AppException ex = assertThrows(AppException.class, () -> service.execute(command));
        assertEquals(ErrorCode.ACCESS_DENIED, ex.getError());
    }

    // ─── Helpers ─────────────────────────────────────────────────────────────────

    /**
     * Creates a User with the given quota settings.
     * Tenant quota is set generously (Long.MAX_VALUE) so tenant quota never blocks these tests.
     */
    private User actor(UUID userId, UUID tenantId, long userStorageUsed, long userStorageQuota) {
        Tenant tenant = Tenant.builder()
                .name("Test Tenant")
                .slug("test-tenant")
                .isActive(true)
                .build();
        tenant.setId(tenantId);
        tenant.setStorageUsedBytes(0L);
        tenant.setStorageQuotaBytes(Long.MAX_VALUE);

        User user = User.builder()
                .tenant(tenant)
                .email("actor@example.com")
                .passwordHash("hash")
                .fullName("Actor")
                .build();
        user.setId(userId);
        user.setStorageUsedBytes(userStorageUsed);
        user.setStorageQuotaBytes(userStorageQuota);
        return user;
    }

    private File buildFile(UUID fileId, UUID tenantId, UUID uploadedById) {
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
                .originalName("report.pdf")
                .storageKey("key/path/file.pdf")
                .mimeType("application/pdf")
                .sizeBytes(1024L)
                .status(FileStatus.PENDING)
                .isIndexed(false)
                .build();
        file.setId(fileId);
        return file;
    }
}
