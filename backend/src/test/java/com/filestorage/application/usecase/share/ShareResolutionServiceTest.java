package com.filestorage.application.usecase.share;

import com.filestorage.adapter.out.persistence.entity.File;
import com.filestorage.adapter.out.persistence.entity.FileShare;
import com.filestorage.adapter.out.persistence.entity.Folder;
import com.filestorage.adapter.out.persistence.entity.Tenant;
import com.filestorage.adapter.out.persistence.entity.User;
import com.filestorage.domain.model.enums.MyPermission;
import com.filestorage.domain.model.enums.PermissionFile;
import com.filestorage.domain.model.enums.PermissionSource;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.share.PermissionResult;
import com.filestorage.domain.port.out.ShareRepositoryPort;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ShareResolutionServiceTest {

    @Mock
    private ShareRepositoryPort shareRepository;

    private ShareResolutionService service;

    @BeforeEach
    void setUp() {
        service = new ShareResolutionService(shareRepository);
    }

    @Test
    void tenantAdminResolvesAsAdminPrivilegeNotOwner() {
        UUID adminId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Folder folder = folder(UUID.randomUUID(), tenantId, UUID.randomUUID());

        PermissionResult result = service.resolveFolder(adminId, tenantId, RoleCode.TENANT_ADMIN, folder);

        assertEquals(MyPermission.ADMIN, result.permission());
        assertEquals(PermissionSource.ADMIN_PRIVILEGE, result.source());
        verifyNoInteractions(shareRepository);
    }

    @Test
    void ownerFileResolvesAsOwnership() {
        UUID ownerId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        File file = file(UUID.randomUUID(), tenantId, ownerId, null);

        PermissionResult result = service.resolveFile(ownerId, tenantId, RoleCode.MEMBER, file);

        assertEquals(MyPermission.OWNER, result.permission());
        assertEquals(PermissionSource.OWNERSHIP, result.source());
        verifyNoInteractions(shareRepository);
    }

    @Test
    void directFolderEditShareResolvesAsEditorDirectShare() {
        UUID actorId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Folder folder = folder(UUID.randomUUID(), tenantId, UUID.randomUUID());
        FileShare share = new FileShare();
        share.setPermission(PermissionFile.EDIT);

        when(shareRepository.findActiveUserFolderShare(folder.getId(), actorId)).thenReturn(Optional.of(share));

        PermissionResult result = service.resolveFolder(actorId, tenantId, RoleCode.MEMBER, folder);

        assertEquals(MyPermission.EDITOR, result.permission());
        assertEquals(PermissionSource.DIRECT_SHARE, result.source());
    }

    @Test
    void tenantWideFileShareResolvesAsViewerDirectShare() {
        UUID actorId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        File file = file(UUID.randomUUID(), tenantId, UUID.randomUUID(), null);

        when(shareRepository.findActiveUserFileShare(file.getId(), actorId)).thenReturn(Optional.empty());
        when(shareRepository.hasActiveTenantFileShare(file.getId(), tenantId)).thenReturn(true);

        PermissionResult result = service.resolveFile(actorId, tenantId, RoleCode.MEMBER, file);

        assertEquals(MyPermission.VIEWER, result.permission());
        assertEquals(PermissionSource.DIRECT_SHARE, result.source());
    }

    @Test
    void ancestorFolderShareResolvesAsInheritedViewer() {
        UUID actorId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Folder folder = folder(UUID.randomUUID(), tenantId, UUID.randomUUID());

        when(shareRepository.findActiveUserFolderShare(folder.getId(), actorId)).thenReturn(Optional.empty());
        when(shareRepository.hasActiveTenantFolderShare(folder.getId(), tenantId)).thenReturn(false);
        when(shareRepository.hasActiveUserShareOnAnyAncestor(folder.getId(), actorId)).thenReturn(true);

        PermissionResult result = service.resolveFolder(actorId, tenantId, RoleCode.MEMBER, folder);

        assertEquals(MyPermission.VIEWER, result.permission());
        assertEquals(PermissionSource.INHERITED, result.source());
    }

    @Test
    void crossTenantMemberGetsNoPermission() {
        UUID actorId = UUID.randomUUID();
        UUID actorTenantId = UUID.randomUUID();
        Folder folder = folder(UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID());

        PermissionResult result = service.resolveFolder(actorId, actorTenantId, RoleCode.MEMBER, folder);

        assertNull(result);
        verifyNoInteractions(shareRepository);
    }

    private Folder folder(UUID folderId, UUID tenantId, UUID ownerId) {
        Tenant tenant = tenant(tenantId);
        User owner = user(ownerId, tenant);
        Folder folder = Folder.builder()
                .tenant(tenant)
                .owner(owner)
                .createdBy(owner)
                .name("Folder")
                .build();
        folder.setId(folderId);
        return folder;
    }

    private File file(UUID fileId, UUID tenantId, UUID ownerId, Folder folder) {
        Tenant tenant = tenant(tenantId);
        User owner = user(ownerId, tenant);
        File file = File.builder()
                .tenant(tenant)
                .uploadedBy(owner)
                .folder(folder)
                .originalName("doc.pdf")
                .storageKey("key")
                .sizeBytes(10L)
                .build();
        file.setId(fileId);
        return file;
    }

    private Tenant tenant(UUID tenantId) {
        Tenant tenant = Tenant.builder().name("Tenant").slug("tenant").build();
        tenant.setId(tenantId);
        return tenant;
    }

    private User user(UUID userId, Tenant tenant) {
        User user = User.builder()
                .tenant(tenant)
                .email(userId + "@example.com")
                .passwordHash("hash")
                .fullName("User")
                .build();
        user.setId(userId);
        return user;
    }
}
