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
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ConfirmUploadServiceTest {

    @Mock
    private FileJpaRepository fileRepository;
    @Mock
    private ObjectStoragePort objectStoragePort;
    @Mock
    private UploadStorageVerifier uploadStorageVerifier;

    private ConfirmUploadService service;

    @BeforeEach
    void setUp() {
        service = new ConfirmUploadService(fileRepository, objectStoragePort, uploadStorageVerifier);
    }

    // ─── Happy path ───────────────────────────────────────────────────────────

    @Test
    void ownerCanConfirmOwnPendingFile() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        File file = buildFile(fileId, tenantId, userId, FileStatus.PENDING);

        when(fileRepository.findByIdAndTenantId(fileId, tenantId)).thenReturn(Optional.of(file));
        when(objectStoragePort.objectExists(file.getStorageKey())).thenReturn(true);
        when(fileRepository.save(any())).thenReturn(file);
        // verifySizeOrFail and verifyMimeOrFail are no-ops (mock default)
        when(uploadStorageVerifier.routeToScanOrActivate(eq(file), eq(tenantId), eq(userId)))
                .thenReturn(FileStatus.SCANNING);

        ConfirmUploadView view = service.execute(command(userId, tenantId, fileId));

        assertEquals(fileId, view.fileId());
        assertEquals(FileStatus.SCANNING, view.status());
        verify(uploadStorageVerifier).verifySizeOrFail(file, file.getStorageKey());
        verify(uploadStorageVerifier).verifyMimeOrFail(file, file.getStorageKey());
        verify(uploadStorageVerifier).routeToScanOrActivate(file, tenantId, userId);
    }

    @Test
    void largeTrustedMediaFileBypassesScanAndBecomesActive() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        File file = buildFile(fileId, tenantId, userId, FileStatus.PENDING, "video/mp4", 60L * 1024 * 1024);

        when(fileRepository.findByIdAndTenantId(fileId, tenantId)).thenReturn(Optional.of(file));
        when(objectStoragePort.objectExists(file.getStorageKey())).thenReturn(true);
        when(fileRepository.save(any())).thenReturn(file);
        when(uploadStorageVerifier.routeToScanOrActivate(eq(file), eq(tenantId), eq(userId)))
                .thenReturn(FileStatus.ACTIVE);

        ConfirmUploadView view = service.execute(command(userId, tenantId, fileId));

        assertEquals(FileStatus.ACTIVE, view.status());
    }

    // ─── Access control ───────────────────────────────────────────────────────

    @Test
    void nonOwnerMemberCannotConfirmOtherUsersFile() {
        UUID ownerId = UUID.randomUUID();
        UUID actorId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        File file = buildFile(fileId, tenantId, ownerId, FileStatus.PENDING);

        when(fileRepository.findByIdAndTenantId(fileId, tenantId)).thenReturn(Optional.of(file));

        AppException ex = assertThrows(AppException.class,
                () -> service.execute(command(actorId, tenantId, fileId)));

        assertEquals(ErrorCode.ACCESS_DENIED, ex.getError());
        verify(fileRepository, never()).save(any());
    }

    /**
     * Owner-only rule applies regardless of role.
     * Even TENANT_ADMIN cannot confirm another user's file.
     */
    @Test
    void tenantAdminCannotConfirmOtherUsersFile() {
        UUID ownerId = UUID.randomUUID();
        UUID adminId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        File file = buildFile(fileId, tenantId, ownerId, FileStatus.PENDING);

        when(fileRepository.findByIdAndTenantId(fileId, tenantId)).thenReturn(Optional.of(file));

        var cmd = new ConfirmUploadUseCase.ConfirmUploadCommand(adminId, tenantId, RoleCode.TENANT_ADMIN, fileId);
        AppException ex = assertThrows(AppException.class, () -> service.execute(cmd));

        assertEquals(ErrorCode.ACCESS_DENIED, ex.getError());
        verify(fileRepository, never()).save(any());
    }

    @Test
    void tenantAdminCanConfirmOwnPendingFile() {
        UUID adminId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        File file = buildFile(fileId, tenantId, adminId, FileStatus.PENDING);

        when(fileRepository.findByIdAndTenantId(fileId, tenantId)).thenReturn(Optional.of(file));
        when(objectStoragePort.objectExists(file.getStorageKey())).thenReturn(true);
        when(fileRepository.save(any())).thenReturn(file);
        when(uploadStorageVerifier.routeToScanOrActivate(any(), any(), any()))
                .thenReturn(FileStatus.SCANNING);

        var cmd = new ConfirmUploadUseCase.ConfirmUploadCommand(adminId, tenantId, RoleCode.TENANT_ADMIN, fileId);
        ConfirmUploadView view = service.execute(cmd);

        assertEquals(FileStatus.SCANNING, view.status());
    }

    // ─── Guard failures ───────────────────────────────────────────────────────

    @Test
    void confirmFailsWhenFileNotInPendingStatus() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        File file = buildFile(fileId, tenantId, userId, FileStatus.ACTIVE);

        when(fileRepository.findByIdAndTenantId(fileId, tenantId)).thenReturn(Optional.of(file));

        AppException ex = assertThrows(AppException.class,
                () -> service.execute(command(userId, tenantId, fileId)));

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

        AppException ex = assertThrows(AppException.class,
                () -> service.execute(command(userId, tenantId, fileId)));

        assertEquals(ErrorCode.FILE_NOT_FOUND_ON_STORAGE, ex.getError());
        verify(uploadStorageVerifier, never()).verifySizeOrFail(any(), anyString());
    }

    // ─── UploadStorageVerifier delegation ─────────────────────────────────────

    @Test
    void confirmFailsWhenSizeMismatch() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        File file = buildFile(fileId, tenantId, userId, FileStatus.PENDING);

        when(fileRepository.findByIdAndTenantId(fileId, tenantId)).thenReturn(Optional.of(file));
        when(objectStoragePort.objectExists(file.getStorageKey())).thenReturn(true);
        when(fileRepository.save(any())).thenReturn(file);
        doThrow(new AppException(ErrorCode.FILE_METADATA_MISMATCH))
                .when(uploadStorageVerifier).verifySizeOrFail(eq(file), anyString());

        AppException ex = assertThrows(AppException.class,
                () -> service.execute(command(userId, tenantId, fileId)));

        assertEquals(ErrorCode.FILE_METADATA_MISMATCH, ex.getError());
        verify(uploadStorageVerifier, never()).verifyMimeOrFail(any(), anyString());
    }

    @Test
    void mimeSpoofingCausesFailureBeforeScanPublished() {
        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        File file = buildFile(fileId, tenantId, userId, FileStatus.PENDING);

        when(fileRepository.findByIdAndTenantId(fileId, tenantId)).thenReturn(Optional.of(file));
        when(objectStoragePort.objectExists(file.getStorageKey())).thenReturn(true);
        when(fileRepository.save(any())).thenReturn(file);
        doThrow(new AppException(ErrorCode.UNSUPPORTED_FILE_TYPE))
                .when(uploadStorageVerifier).verifyMimeOrFail(eq(file), anyString());

        AppException ex = assertThrows(AppException.class,
                () -> service.execute(command(userId, tenantId, fileId)));

        assertEquals(ErrorCode.UNSUPPORTED_FILE_TYPE, ex.getError());
        verify(uploadStorageVerifier, never()).routeToScanOrActivate(any(), any(), any());
    }

    // ─── Helpers ──────────────────────────────────────────────────────────────

    private static ConfirmUploadUseCase.ConfirmUploadCommand command(UUID userId, UUID tenantId, UUID fileId) {
        return new ConfirmUploadUseCase.ConfirmUploadCommand(userId, tenantId, RoleCode.MEMBER, fileId);
    }

    private File buildFile(UUID fileId, UUID tenantId, UUID uploadedById, FileStatus status) {
        return buildFile(fileId, tenantId, uploadedById, status, "application/pdf", 1024L);
    }

    private File buildFile(UUID fileId, UUID tenantId, UUID uploadedById, FileStatus status,
                           String mimeType, long sizeBytes) {
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
                .mimeType(mimeType)
                .sizeBytes(sizeBytes)
                .status(status)
                .isIndexed(false)
                .build();
        file.setId(fileId);
        return file;
    }
}
