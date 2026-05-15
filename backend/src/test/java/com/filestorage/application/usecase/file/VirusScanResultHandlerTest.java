package com.filestorage.application.usecase.file;

import com.filestorage.adapter.out.persistence.entity.File;
import com.filestorage.adapter.out.persistence.entity.Tenant;
import com.filestorage.adapter.out.persistence.entity.User;
import com.filestorage.adapter.out.persistence.repository.FileJpaRepository;
import com.filestorage.adapter.out.persistence.repository.UserJpaRepository;
import com.filestorage.domain.model.enums.FileStatus;
import com.filestorage.domain.port.out.ObjectStoragePort;
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
class VirusScanResultHandlerTest {

    @Mock
    private FileJpaRepository fileRepository;
    @Mock
    private UserJpaRepository userRepository;
    @Mock
    private ObjectStoragePort objectStoragePort;

    private VirusScanResultHandler handler;

    @BeforeEach
    void setUp() {
        handler = new VirusScanResultHandler(fileRepository, userRepository, objectStoragePort);
    }

    @Test
    void cleanScanForInactiveUploaderIncrementsOnlyUserUsage() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = Tenant.builder().name("Tenant").slug("tenant").storageUsedBytes(30L).isActive(true).build();
        tenant.setId(tenantId);
        User uploader = User.builder()
                .tenant(tenant)
                .email("uploader@example.com")
                .passwordHash("hash")
                .fullName("Uploader")
                .isActive(false)
                .storageQuotaBytes(100L)
                .storageUsedBytes(40L)
                .build();
        uploader.setId(UUID.randomUUID());
        File file = File.builder()
                .tenant(tenant)
                .uploadedBy(uploader)
                .originalName("report.pdf")
                .storageKey("tenant/uploader/report")
                .mimeType("application/pdf")
                .sizeBytes(10L)
                .status(FileStatus.SCANNING)
                .isIndexed(false)
                .build();

        when(userRepository.incrementStorageUsedIfQuotaAllows(uploader.getId(), 10L)).thenReturn(1);
        when(fileRepository.save(any(File.class))).thenAnswer(invocation -> invocation.getArgument(0));

        handler.handleClean(file);

        verify(userRepository).incrementStorageUsedIfQuotaAllows(uploader.getId(), 10L);
        assertEquals(40L, uploader.getStorageUsedBytes());
        assertEquals(30L, tenant.getStorageUsedBytes());
        assertEquals(FileStatus.ACTIVE, file.getStatus());
    }

    @Test
    void cleanScanForActiveUploaderIncrementsOnlyUserUsage() {
        UUID tenantId = UUID.randomUUID();
        Tenant tenant = Tenant.builder().name("Tenant").slug("tenant").storageUsedBytes(30L).isActive(true).build();
        tenant.setId(tenantId);
        User uploader = User.builder()
                .tenant(tenant)
                .email("uploader@example.com")
                .passwordHash("hash")
                .fullName("Uploader")
                .isActive(true)
                .storageQuotaBytes(100L)
                .storageUsedBytes(40L)
                .build();
        uploader.setId(UUID.randomUUID());
        File file = File.builder()
                .tenant(tenant)
                .uploadedBy(uploader)
                .originalName("report.pdf")
                .storageKey("tenant/uploader/report")
                .mimeType("application/pdf")
                .sizeBytes(10L)
                .status(FileStatus.SCANNING)
                .isIndexed(false)
                .build();

        when(userRepository.incrementStorageUsedIfQuotaAllows(uploader.getId(), 10L)).thenReturn(1);
        when(fileRepository.save(any(File.class))).thenAnswer(invocation -> invocation.getArgument(0));

        handler.handleClean(file);

        verify(userRepository).incrementStorageUsedIfQuotaAllows(uploader.getId(), 10L);
        assertEquals(40L, uploader.getStorageUsedBytes());
        assertEquals(30L, tenant.getStorageUsedBytes());
        assertEquals(FileStatus.ACTIVE, file.getStatus());
    }
}
