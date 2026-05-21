package com.filestorage.application.usecase.share;

import com.filestorage.adapter.out.persistence.entity.FileShare;
import com.filestorage.adapter.out.persistence.entity.Tenant;
import com.filestorage.adapter.out.persistence.repository.FileJpaRepository;
import com.filestorage.adapter.out.persistence.repository.FolderJpaRepository;
import com.filestorage.adapter.out.persistence.repository.TenantJpaRepository;
import com.filestorage.adapter.out.persistence.repository.UserJpaRepository;
import com.filestorage.domain.model.enums.PermissionFile;
import com.filestorage.domain.model.enums.ShareStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShareServiceSupportTest {

    @Mock
    private UserJpaRepository userRepository;
    @Mock
    private TenantJpaRepository tenantRepository;
    @Mock
    private FileJpaRepository fileRepository;
    @Mock
    private FolderJpaRepository folderRepository;

    private ShareServiceSupport support;

    @BeforeEach
    void setUp() {
        support = new ShareServiceSupport(userRepository, tenantRepository, fileRepository, folderRepository);
    }

    @Test
    void toShareViewIncludesTenantName() {
        UUID tenantId = UUID.randomUUID();
        FileShare share = new FileShare();
        share.setId(UUID.randomUUID());
        share.setTenantId(tenantId);
        share.setFileId(UUID.randomUUID());
        share.setPermission(PermissionFile.VIEW);
        share.setStatus(ShareStatus.ACTIVE);
        share.setExpiresAt(Instant.now().plusSeconds(3600));

        Tenant tenant = Tenant.builder().name("Tenant A").slug("tenant-a").build();
        tenant.setId(tenantId);
        when(tenantRepository.findById(tenantId)).thenReturn(Optional.of(tenant));

        var view = support.toShareView(share);

        assertEquals(tenantId, view.tenantId());
        assertEquals("Tenant A", view.tenantName());
    }
}
