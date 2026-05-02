package com.filestorage.application.usecase.file;

import com.filestorage.adapter.out.persistence.entity.File;
import com.filestorage.adapter.out.persistence.entity.Tenant;
import com.filestorage.adapter.out.persistence.entity.User;
import com.filestorage.adapter.out.persistence.repository.FileJpaRepository;
import com.filestorage.domain.model.enums.FileStatus;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.file.ConfirmUploadView;
import com.filestorage.domain.port.in.file.ConfirmUploadUseCase;
import com.filestorage.domain.port.out.ObjectStoragePort;
import com.filestorage.domain.port.out.VirusScanEventPublisherPort;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfirmUploadServiceTest {

    @Mock
    private FileJpaRepository fileRepository;
    @Mock
    private FileSupport fileSupport;
    @Mock
    private ObjectStoragePort objectStoragePort;
    @Mock
    private VirusScanEventPublisherPort virusScanEventPublisherPort;

    private ConfirmUploadService service;

    @BeforeEach
    void setUp() {
        service = new ConfirmUploadService(fileRepository, fileSupport, objectStoragePort, virusScanEventPublisherPort);
    }

    @Test
    void ownerCanConfirmOwnPendingFile() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        File file = buildFile(fileId, tenantId, userId, FileStatus.PENDING);

        when(fileRepository.findByIdAndTenantId(fileId, tenantId)).thenReturn(Optional.of(file));
        when(objectStoragePort.objectExists(file.getStorageKey())).thenReturn(true);
        when(objectStoragePort.getObjectMetadata(file.getStorageKey()))
                .thenReturn(new ObjectStoragePort.ObjectMetadata(1024L, "application/pdf"));
        when(fileRepository.save(any())).thenReturn(file);

        var command = new ConfirmUploadUseCase.ConfirmUploadCommand(userId, tenantId, RoleCode.MEMBER, fileId);

        ConfirmUploadView view = service.execute(command);

        assertEquals(fileId, view.fileId());
        assertEquals(FileStatus.SCANNING, view.status());
        verify(virusScanEventPublisherPort).publishVirusScanRequested(any());
    }

    @Test
    void nonOwnerMemberCannotConfirmOtherUsersFile() {
        UUID ownerId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID(); // different user
        UUID tenantId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        File file = buildFile(fileId, tenantId, ownerId, FileStatus.PENDING);

        when(fileRepository.findByIdAndTenantId(fileId, tenantId)).thenReturn(Optional.of(file));

        var command = new ConfirmUploadUseCase.ConfirmUploadCommand(actorId, tenantId, RoleCode.MEMBER, fileId);

        AppException ex = assertThrows(AppException.class, () -> service.execute(command));
        assertEquals(ErrorCode.ACCESS_DENIED, ex.getError());
        verify(fileRepository, never()).save(any());
    }

    /**
     * Service enforces owner-only rule regardless of role.
     * TENANT_ADMIN confirming another user's file also gets ACCESS_DENIED.
     */
    @Test
    void tenantAdminCannotConfirmOtherUsersFile() {
        UUID ownerId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID(); // different from owner
        UUID tenantId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        File file = buildFile(fileId, tenantId, ownerId, FileStatus.PENDING);

        when(fileRepository.findByIdAndTenantId(fileId, tenantId)).thenReturn(Optional.of(file));

        var command = new ConfirmUploadUseCase.ConfirmUploadCommand(adminId, tenantId, RoleCode.TENANT_ADMIN, fileId);

        AppException ex = assertThrows(AppException.class, () -> service.execute(command));
        assertEquals(ErrorCode.ACCESS_DENIED, ex.getError());
        verify(fileRepository, never()).save(any());
    }

    @Test
    void tenantAdminCanConfirmOwnPendingFile() {
        UUID adminId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        File file = buildFile(fileId, tenantId, adminId, FileStatus.PENDING); // admin is the owner

        when(fileRepository.findByIdAndTenantId(fileId, tenantId)).thenReturn(Optional.of(file));
        when(objectStoragePort.objectExists(file.getStorageKey())).thenReturn(true);
        when(objectStoragePort.getObjectMetadata(file.getStorageKey()))
                .thenReturn(new ObjectStoragePort.ObjectMetadata(1024L, "application/pdf"));
        when(fileRepository.save(any())).thenReturn(file);

        var command = new ConfirmUploadUseCase.ConfirmUploadCommand(adminId, tenantId, RoleCode.TENANT_ADMIN, fileId);

        ConfirmUploadView view = service.execute(command);

        assertEquals(fileId, view.fileId());
        assertEquals(FileStatus.SCANNING, view.status());
    }

    @Test
    void confirmFailsWhenFileNotInPendingStatus() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        File file = buildFile(fileId, tenantId, userId, FileStatus.ACTIVE); // already active

        when(fileRepository.findByIdAndTenantId(fileId, tenantId)).thenReturn(Optional.of(file));

        var command = new ConfirmUploadUseCase.ConfirmUploadCommand(userId, tenantId, RoleCode.MEMBER, fileId);

        AppException ex = assertThrows(AppException.class, () -> service.execute(command));
        assertEquals(ErrorCode.INVALID_FILE_STATUS, ex.getError());
        verify(objectStoragePort, never()).objectExists(anyString());
    }

    @Test
    void confirmFailsWhenObjectNotFoundInStorage() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        File file = buildFile(fileId, tenantId, userId, FileStatus.PENDING);

        when(fileRepository.findByIdAndTenantId(fileId, tenantId)).thenReturn(Optional.of(file));
        when(objectStoragePort.objectExists(file.getStorageKey())).thenReturn(false);
        when(fileRepository.save(any())).thenReturn(file);

        var command = new ConfirmUploadUseCase.ConfirmUploadCommand(userId, tenantId, RoleCode.MEMBER, fileId);

        AppException ex = assertThrows(AppException.class, () -> service.execute(command));
        assertEquals(ErrorCode.FILE_NOT_FOUND_ON_STORAGE, ex.getError());
        verify(virusScanEventPublisherPort, never()).publishVirusScanRequested(any());
    }

    @Test
    void confirmFailsWhenSizeMismatch() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        File file = buildFile(fileId, tenantId, userId, FileStatus.PENDING);
        // declared 1024 bytes; actual is different
        ObjectStoragePort.ObjectMetadata mismatch = new ObjectStoragePort.ObjectMetadata(9999L, "application/pdf");

        when(fileRepository.findByIdAndTenantId(fileId, tenantId)).thenReturn(Optional.of(file));
        when(objectStoragePort.objectExists(file.getStorageKey())).thenReturn(true);
        when(objectStoragePort.getObjectMetadata(file.getStorageKey())).thenReturn(mismatch);
        when(fileRepository.save(any())).thenReturn(file);

        var command = new ConfirmUploadUseCase.ConfirmUploadCommand(userId, tenantId, RoleCode.MEMBER, fileId);

        AppException ex = assertThrows(AppException.class, () -> service.execute(command));
        assertEquals(ErrorCode.FILE_METADATA_MISMATCH, ex.getError());
        verify(virusScanEventPublisherPort, never()).publishVirusScanRequested(any());
    }

    @Test
    void confirmFailsWhenContentTypeMismatch() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        File file = buildFile(fileId, tenantId, userId, FileStatus.PENDING);
        // declared "application/pdf"; actual content-type is different
        ObjectStoragePort.ObjectMetadata mismatch =
                new ObjectStoragePort.ObjectMetadata(1024L, "text/plain");

        when(fileRepository.findByIdAndTenantId(fileId, tenantId)).thenReturn(Optional.of(file));
        when(objectStoragePort.objectExists(file.getStorageKey())).thenReturn(true);
        when(objectStoragePort.getObjectMetadata(file.getStorageKey())).thenReturn(mismatch);
        when(fileRepository.save(any())).thenReturn(file);

        var command = new ConfirmUploadUseCase.ConfirmUploadCommand(userId, tenantId, RoleCode.MEMBER, fileId);

        AppException ex = assertThrows(AppException.class, () -> service.execute(command));
        assertEquals(ErrorCode.FILE_METADATA_MISMATCH, ex.getError());
        verify(virusScanEventPublisherPort, never()).publishVirusScanRequested(any());
    }

    @Test
    void contentTypeWithCharsetStillMatches() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        File file = buildFile(fileId, tenantId, userId, FileStatus.PENDING);
        // Storage returns content-type with charset parameter; declared type is without
        ObjectStoragePort.ObjectMetadata withCharset =
                new ObjectStoragePort.ObjectMetadata(1024L, "application/pdf; charset=utf-8");

        when(fileRepository.findByIdAndTenantId(fileId, tenantId)).thenReturn(Optional.of(file));
        when(objectStoragePort.objectExists(file.getStorageKey())).thenReturn(true);
        when(objectStoragePort.getObjectMetadata(file.getStorageKey())).thenReturn(withCharset);
        when(fileRepository.save(any())).thenReturn(file);

        var command = new ConfirmUploadUseCase.ConfirmUploadCommand(userId, tenantId, RoleCode.MEMBER, fileId);

        ConfirmUploadView view = service.execute(command);

        assertEquals(FileStatus.SCANNING, view.status());
        verify(virusScanEventPublisherPort).publishVirusScanRequested(any());
    }


    // ─── Helpers ─────────────────────────────────────────────────────────────────

    private File buildFile(UUID fileId, UUID tenantId, UUID uploadedById, FileStatus status) {
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
                .storageKey("tenant/user/uuid.pdf")
                .mimeType("application/pdf")
                .sizeBytes(1024L)
                .status(status)
                .isIndexed(false)
                .build();
        file.setId(fileId);
        return file;
    }
}
