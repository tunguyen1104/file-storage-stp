package com.filestorage.application.usecase.folder;

import com.filestorage.adapter.out.persistence.repository.FolderJpaRepository;
import com.filestorage.adapter.out.persistence.repository.UserJpaRepository;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FolderSupportTest {

    @Mock
    private UserJpaRepository userRepository;
    @Mock
    private FolderJpaRepository folderRepository;

    private FolderSupport support;

    @BeforeEach
    void setUp() {
        support = new FolderSupport(userRepository, folderRepository);
    }

    @Test
    void memberCannotFilterByAnotherUser() {
        UUID actorUserId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID createdBy = UUID.randomUUID();

        AppException ex = assertThrows(AppException.class, () ->
                support.validateCreatedByFilter(actorUserId, tenantId, RoleCode.MEMBER, createdBy)
        );

        assertEquals(ErrorCode.FORBIDDEN, ex.getError());
        verifyNoInteractions(userRepository);
    }

    @Test
    void tenantAdminCannotFilterByUserOutsideTenant() {
        UUID actorUserId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID createdBy = UUID.randomUUID();
        when(userRepository.countByIdsAndTenantId(List.of(createdBy), tenantId)).thenReturn(0L);

        AppException ex = assertThrows(AppException.class, () ->
                support.validateCreatedByFilter(actorUserId, tenantId, RoleCode.TENANT_ADMIN, createdBy)
        );

        assertEquals(ErrorCode.FORBIDDEN, ex.getError());
    }

    @Test
    void tenantAdminCanFilterByUserInSameTenant() {
        UUID actorUserId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID createdBy = UUID.randomUUID();
        when(userRepository.countByIdsAndTenantId(List.of(createdBy), tenantId)).thenReturn(1L);

        assertDoesNotThrow(() ->
                support.validateCreatedByFilter(actorUserId, tenantId, RoleCode.TENANT_ADMIN, createdBy)
        );
    }
}
