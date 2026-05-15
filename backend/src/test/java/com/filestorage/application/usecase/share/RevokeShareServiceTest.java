package com.filestorage.application.usecase.share;

import com.filestorage.adapter.out.persistence.entity.File;
import com.filestorage.adapter.out.persistence.entity.FileShare;
import com.filestorage.adapter.out.persistence.entity.Tenant;
import com.filestorage.adapter.out.persistence.entity.User;
import com.filestorage.domain.model.enums.ShareStatus;
import com.filestorage.domain.port.out.ShareRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RevokeShareServiceTest {

    @Mock
    private ShareRepositoryPort shareRepository;
    @Mock
    private ShareServiceSupport support;

    private RevokeShareService service;

    @BeforeEach
    void setUp() {
        service = new RevokeShareService(shareRepository, support);
    }

    @Test
    void revokeShareSetsRevokedStatusAndTimestamp() {
        UUID ownerId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID shareId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        User owner = user(ownerId, tenantId);
        File file = file(fileId, tenantId, ownerId);
        FileShare share = new FileShare();
        share.setId(shareId);
        share.setFileId(fileId);
        share.setStatus(ShareStatus.ACTIVE);
        share.setExpiresAt(Instant.now().plusSeconds(3600));

        when(support.resolveActor(ownerId)).thenReturn(owner);
        when(shareRepository.findById(shareId)).thenReturn(Optional.of(share));
        when(support.findShareFile(share)).thenReturn(file);
        when(support.fileOwnerId(file)).thenReturn(ownerId);

        service.revokeShare(shareId, ownerId);

        ArgumentCaptor<FileShare> captor = ArgumentCaptor.forClass(FileShare.class);
        verify(shareRepository).save(captor.capture());
        assertEquals(ShareStatus.REVOKED, captor.getValue().getStatus());
        assertNotNull(captor.getValue().getRevokedAt());
        assertNotNull(captor.getValue().getUpdatedAt());
    }

    private User user(UUID userId, UUID tenantId) {
        Tenant tenant = Tenant.builder().name("Tenant").slug("tenant").build();
        tenant.setId(tenantId);
        User user = User.builder()
                .tenant(tenant)
                .email("owner@example.com")
                .passwordHash("hash")
                .fullName("Owner")
                .build();
        user.setId(userId);
        return user;
    }

    private File file(UUID fileId, UUID tenantId, UUID ownerId) {
        Tenant tenant = Tenant.builder().name("Tenant").slug("tenant").build();
        tenant.setId(tenantId);
        User owner = user(ownerId, tenantId);
        File file = File.builder()
                .tenant(tenant)
                .uploadedBy(owner)
                .originalName("doc.pdf")
                .storageKey("key")
                .sizeBytes(10L)
                .build();
        file.setId(fileId);
        return file;
    }
}
