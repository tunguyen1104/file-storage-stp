package com.filestorage.application.usecase.folder;

import com.filestorage.adapter.out.persistence.entity.Folder;
import com.filestorage.adapter.out.persistence.entity.Tenant;
import com.filestorage.adapter.out.persistence.entity.User;
import com.filestorage.adapter.out.persistence.repository.FolderJpaRepository;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.folder.FolderView;
import com.filestorage.domain.port.in.folder.CreateFolderUseCase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CreateFolderServiceTest {

    @Mock
    private FolderJpaRepository folderRepository;
    @Mock
    private FolderSupport folderSupport;

    private CreateFolderService service;

    @BeforeEach
    void setUp() {
        service = new CreateFolderService(folderRepository, folderSupport);
    }

    @Test
    void createSetsOwnerToCurrentUser() {
        UUID actorUserId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID folderId = UUID.randomUUID();
        User actor = actor(actorUserId, tenantId);

        when(folderSupport.resolveActor(actorUserId, tenantId)).thenReturn(actor);
        when(folderRepository.existsByTenantIdAndParentIdAndName(tenantId, null, "Reports")).thenReturn(false);
        when(folderRepository.save(any())).thenAnswer(invocation -> {
            Folder folder = invocation.getArgument(0);
            folder.setId(folderId);
            return folder;
        });

        FolderView view = service.execute(new CreateFolderUseCase.CreateFolderCommand(
                actorUserId,
                tenantId,
                RoleCode.TENANT_ADMIN,
                " Reports ",
                null
        ));

        assertEquals(actorUserId, view.ownerId());
        verify(folderRepository).save(argThat(saved -> saved.getOwner() != null
                && actorUserId.equals(saved.getOwner().getId())
                && actorUserId.equals(saved.getCreatedBy().getId())));
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
