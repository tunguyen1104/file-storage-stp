package com.filestorage.application.usecase.file;

import com.filestorage.adapter.out.persistence.entity.File;
import com.filestorage.adapter.out.persistence.entity.Tenant;
import com.filestorage.adapter.out.persistence.entity.User;
import com.filestorage.adapter.out.persistence.repository.FileJpaRepository;
import com.filestorage.adapter.out.persistence.repository.UserJpaRepository;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.infrastructure.config.StorageProperties;
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
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileDownloadSupportTest {

    @Mock
    private FileJpaRepository fileRepository;
    @Mock
    private UserJpaRepository userRepository;

    private FileSupport support;

    @BeforeEach
    void setUp() {
        StorageProperties storageProperties = new StorageProperties();
        storageProperties.setDefaultDownloadTtlMinutes(15);
        support = new FileSupport(userRepository, fileRepository, storageProperties);
    }

    @Test
    void resolveActorReturnsUserInSameTenant() {
        UUID actorId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        User actor = actor(actorId, tenantId);
        when(userRepository.findByIdWithRoleAndTenant(actorId)).thenReturn(Optional.of(actor));

        User resolved = support.resolveActor(actorId, tenantId);

        assertSame(actor, resolved);
    }

    @Test
    void resolveActorRejectsCrossTenantUser() {
        UUID actorId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        User actor = actor(actorId, UUID.randomUUID());
        when(userRepository.findByIdWithRoleAndTenant(actorId)).thenReturn(Optional.of(actor));

        AppException ex = assertThrows(AppException.class, () -> support.resolveActor(actorId, tenantId));
        assertEquals(ErrorCode.ACCESS_DENIED, ex.getError());
    }

    @Test
    void resolveTtlMinutesUsesDefaultWhenMissing() {
        assertEquals(15, support.resolveTtlMinutes(null));
    }

    @Test
    void resolveTtlMinutesRejectsNonPositiveValue() {
        AppException ex = assertThrows(AppException.class, () -> support.resolveTtlMinutes(0));
        assertEquals(ErrorCode.INVALID_PAYLOAD, ex.getError());
    }

    @Test
    void findReadableFileUsesGlobalLookupForSuperAdmin() {
        UUID fileId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        File file = File.builder().build();
        file.setId(fileId);

        when(fileRepository.findById(fileId)).thenReturn(Optional.of(file));

        File result = support.findReadableFile(fileId, tenantId, RoleCode.SUPER_ADMIN);

        assertSame(file, result);
        verify(fileRepository).findById(fileId);
        verify(fileRepository, never()).findByIdAndTenantId(fileId, tenantId);
    }

    @Test
    void findReadableFileUsesTenantLookupForNonSuperAdmin() {
        UUID fileId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        File file = File.builder().build();
        file.setId(fileId);

        when(fileRepository.findByIdAndTenantId(fileId, tenantId)).thenReturn(Optional.of(file));

        File result = support.findReadableFile(fileId, tenantId, RoleCode.MEMBER);

        assertSame(file, result);
        verify(fileRepository).findByIdAndTenantId(fileId, tenantId);
        verify(fileRepository, never()).findById(fileId);
    }

    @Test
    void findReadableFileThrowsWhenMissing() {
        UUID fileId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        when(fileRepository.findByIdAndTenantId(fileId, tenantId)).thenReturn(Optional.empty());

        AppException ex = assertThrows(AppException.class,
                () -> support.findReadableFile(fileId, tenantId, RoleCode.TENANT_ADMIN));

        assertEquals(ErrorCode.FILE_NOT_FOUND, ex.getError());
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
}


