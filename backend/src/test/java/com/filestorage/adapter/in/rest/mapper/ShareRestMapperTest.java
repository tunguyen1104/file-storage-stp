package com.filestorage.adapter.in.rest.mapper;

import com.filestorage.domain.model.enums.PermissionFile;
import com.filestorage.domain.model.enums.ShareRecipientType;
import com.filestorage.domain.model.enums.ShareStatus;
import com.filestorage.domain.model.share.PaginationView;
import com.filestorage.domain.model.share.ShareResourceView;
import com.filestorage.domain.model.share.ShareTenantView;
import com.filestorage.domain.model.share.ShareUserView;
import com.filestorage.domain.model.share.ShareView;
import com.filestorage.domain.model.share.SharedByView;
import com.filestorage.domain.model.share.SharedWithMePageView;
import com.filestorage.domain.model.share.SharedWithMeView;
import org.junit.jupiter.api.Test;
import org.mapstruct.factory.Mappers;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class ShareRestMapperTest {

    private final ShareRestMapper mapper = Mappers.getMapper(ShareRestMapper.class);

    @Test
    void mapsShareViewToShareResponse() {
        UUID shareId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID folderId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID sharedById = UUID.randomUUID();
        Instant expiresAt = Instant.now().plusSeconds(3600);
        Instant createdAt = Instant.now();

        var view = new ShareView(
                shareId,
                tenantId,
                "folder",
                null,
                folderId,
                PermissionFile.EDIT,
                ShareStatus.ACTIVE,
                ShareRecipientType.USER,
                new ShareUserView(userId, "user@example.com", "User"),
                null,
                new SharedByView(sharedById, "Owner"),
                expiresAt,
                createdAt,
                null
        );

        var response = mapper.toShareResponse(view);

        assertEquals(shareId, response.id());
        assertEquals(tenantId, response.tenantId());
        assertEquals("folder", response.resourceType());
        assertEquals(folderId, response.folderId());
        assertEquals(PermissionFile.EDIT, response.permission());
        assertEquals("user@example.com", response.sharedWithUser().email());
        assertEquals(sharedById, response.sharedBy().id());
        assertNull(response.sharedWithTenant());
        assertEquals(expiresAt, response.expiresAt());
    }

    @Test
    void mapsSharedWithMePageToCommonPageResponse() {
        UUID shareId = UUID.randomUUID();
        UUID resourceId = UUID.randomUUID();
        UUID sharedById = UUID.randomUUID();
        Instant expiresAt = Instant.now().plusSeconds(3600);

        var view = new SharedWithMePageView(
                List.of(new SharedWithMeView(
                        shareId,
                        "file",
                        PermissionFile.VIEW,
                        ShareStatus.ACTIVE,
                        ShareRecipientType.USER,
                        expiresAt,
                        new SharedByView(sharedById, "Owner"),
                        new ShareResourceView("file", resourceId, "report.pdf", "application/pdf", 128L)
                )),
                new PaginationView(2, 5, 6L, 2)
        );

        var response = mapper.toSharedWithMePageResponse(view);

        assertEquals(2, response.getCurrentPage());
        assertEquals(5, response.getPageSize());
        assertEquals(6L, response.getTotalElements());
        assertEquals(2, response.getTotalPages());
        assertEquals(1, response.getData().size());
        assertEquals(shareId, response.getData().get(0).shareId());
        assertEquals(resourceId, response.getData().get(0).resource().id());
        assertEquals(sharedById, response.getData().get(0).sharedBy().id());
    }
}
