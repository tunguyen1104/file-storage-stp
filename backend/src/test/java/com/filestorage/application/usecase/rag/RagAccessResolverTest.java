package com.filestorage.application.usecase.rag;

import com.filestorage.adapter.out.persistence.repository.FileShareJpaRepository;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.rag.RagAccessScope;
import com.filestorage.shared.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagAccessResolverTest {

    @Mock
    private FileShareJpaRepository fileShareRepository;

    private RagAccessResolver resolver;

    @BeforeEach
    void setUp() {
        resolver = new RagAccessResolver(fileShareRepository);
    }

    @Test
    void memberScopeIncludesAccessibleFileAndFolderShares() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        UUID folderId = UUID.randomUUID();
        when(fileShareRepository.findAccessibleFileIdsForUser(eq(userId), eq(tenantId), any(Instant.class)))
                .thenReturn(List.of(fileId));
        when(fileShareRepository.findAccessibleFolderIdClosureForUser(eq(userId), eq(tenantId), any(Instant.class)))
                .thenReturn(List.of(folderId));

        RagAccessScope scope = resolver.resolve(tenantId, userId, RoleCode.MEMBER);

        assertEquals(tenantId, scope.tenantId());
        assertEquals(userId, scope.userId());
        assertEquals(RoleCode.MEMBER, scope.role());
        assertEquals(List.of(fileId), scope.sharedFileIds());
        assertEquals(List.of(folderId), scope.sharedFolderIds());
    }

    @Test
    void adminScopesDoNotFetchShares() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        RagAccessScope tenantAdmin = resolver.resolve(tenantId, userId, RoleCode.TENANT_ADMIN);
        RagAccessScope superAdmin = resolver.resolve(tenantId, userId, RoleCode.SUPER_ADMIN);

        assertEquals(RoleCode.TENANT_ADMIN, tenantAdmin.role());
        assertEquals(RoleCode.SUPER_ADMIN, superAdmin.role());
        assertEquals(tenantId, superAdmin.tenantId());
        verify(fileShareRepository, never()).findAccessibleFileIdsForUser(any(), any(), any());
        verify(fileShareRepository, never()).findAccessibleFolderIdClosureForUser(any(), any(), any());
    }

    @Test
    void missingTenantIsRejected() {
        assertThrows(AppException.class, () ->
                resolver.resolve(null, UUID.randomUUID(), RoleCode.SUPER_ADMIN));
    }
}
