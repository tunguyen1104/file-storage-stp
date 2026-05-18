package com.filestorage.application.usecase.share;

import com.filestorage.adapter.out.persistence.entity.File;
import com.filestorage.adapter.out.persistence.entity.FileShare;
import com.filestorage.adapter.out.persistence.entity.Tenant;
import com.filestorage.adapter.out.persistence.entity.User;
import com.filestorage.domain.model.enums.ShareRecipientType;
import com.filestorage.domain.model.enums.ShareStatus;
import com.filestorage.domain.model.share.ShareView;
import com.filestorage.domain.port.in.share.ShareFileCommand;
import com.filestorage.domain.port.out.ShareRepositoryPort;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShareFileServiceTest {

    @Mock
    private ShareRepositoryPort shareRepository;
    @Mock
    private ShareServiceSupport support;

    private ShareFileService service;

    @BeforeEach
    void setUp() {
        service = new ShareFileService(shareRepository, support);
    }

    @Test
    void shareFileRejectsDuplicateActiveShare() {
        UUID tenantId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        Instant expiresAt = Instant.now().plusSeconds(3600);
        User actor = user(ownerId, tenantId);
        User target = user(targetId, tenantId);
        File file = file(fileId, tenantId, ownerId);

        when(support.resolveActor(ownerId, tenantId)).thenReturn(actor);
        when(support.tenantId(actor)).thenReturn(tenantId);
        when(support.findShareableFile(fileId, tenantId)).thenReturn(file);
        when(support.fileOwnerId(file)).thenReturn(ownerId);
        when(support.resolveTargetUserByEmail("target@example.com")).thenReturn(target);
        when(support.tenantId(target)).thenReturn(tenantId);
        when(shareRepository.existsActiveUserFileShare(fileId, targetId)).thenReturn(true);

        AppException ex = assertThrows(AppException.class, () -> service.shareFile(new ShareFileCommand(
                ownerId,
                tenantId,
                null,
                fileId,
                "target@example.com",
                expiresAt
        )));

        assertEquals(ErrorCode.SHARE_ALREADY_EXISTS, ex.getError());
        verify(shareRepository, never()).save(any());
    }

    @Test
    void shareFileSavesUserShare() {
        UUID tenantId = UUID.randomUUID();
        UUID ownerId = UUID.randomUUID();
        UUID targetId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        UUID shareId = UUID.randomUUID();
        Instant expiresAt = Instant.now().plusSeconds(3600);
        User actor = user(ownerId, tenantId);
        User target = user(targetId, tenantId);
        File file = file(fileId, tenantId, ownerId);
        FileShare newShare = new FileShare();
        ShareView expected = new ShareView(
                shareId,
                tenantId,
                "file",
                fileId,
                null,
                null,
                ShareStatus.ACTIVE,
                ShareRecipientType.USER,
                null,
                null,
                null,
                expiresAt,
                null,
                null
        );

        when(support.resolveActor(ownerId, tenantId)).thenReturn(actor);
        when(support.tenantId(actor)).thenReturn(tenantId);
        when(support.findShareableFile(fileId, tenantId)).thenReturn(file);
        when(support.fileOwnerId(file)).thenReturn(ownerId);
        when(support.resolveTargetUserByEmail("target@example.com")).thenReturn(target);
        when(support.tenantId(target)).thenReturn(tenantId);
        when(shareRepository.existsActiveUserFileShare(fileId, targetId)).thenReturn(false);
        when(support.newShare(tenantId, ownerId, expiresAt)).thenReturn(newShare);
        when(shareRepository.save(newShare)).thenAnswer(invocation -> {
            FileShare saved = invocation.getArgument(0);
            saved.setId(shareId);
            return saved;
        });
        when(support.toShareView(any(FileShare.class))).thenReturn(expected);

        ShareView result = service.shareFile(new ShareFileCommand(
                ownerId,
                tenantId,
                null,
                fileId,
                "target@example.com",
                expiresAt
        ));

        ArgumentCaptor<FileShare> captor = ArgumentCaptor.forClass(FileShare.class);
        verify(shareRepository).save(captor.capture());
        assertEquals(fileId, captor.getValue().getFileId());
        assertEquals(targetId, captor.getValue().getSharedWithUserId());
        assertEquals(expected, result);
    }

    private User user(UUID userId, UUID tenantId) {
        Tenant tenant = Tenant.builder().name("Tenant").slug("tenant").build();
        tenant.setId(tenantId);
        User user = User.builder()
                .tenant(tenant)
                .email("target@example.com")
                .passwordHash("hash")
                .fullName("User")
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
