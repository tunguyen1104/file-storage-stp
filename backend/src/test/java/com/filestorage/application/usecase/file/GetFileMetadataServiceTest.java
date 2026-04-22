package com.filestorage.application.usecase.file;

import com.filestorage.adapter.out.persistence.entity.File;
import com.filestorage.adapter.out.persistence.entity.Tenant;
import com.filestorage.adapter.out.persistence.entity.User;
import com.filestorage.adapter.out.persistence.mapper.FileViewMapper;
import com.filestorage.adapter.out.persistence.repository.FileJpaRepository;
import com.filestorage.domain.model.enums.FileStatus;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.file.FileView;
import com.filestorage.domain.port.in.file.GetFileMetadataUseCase;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GetFileMetadataServiceTest {

    @Mock
    private FileJpaRepository fileRepository;
    @Mock
    private FileSupport fileSupport;
    @Mock
    private FileViewMapper fileViewMapper;

    private GetFileMetadataService service;

    @BeforeEach
    void setUp() {
        service = new GetFileMetadataService(fileSupport, fileViewMapper);
    }

    @Test
    void memberCannotReadOtherUploaderFile() {
        UUID actorUserId = UUID.randomUUID();
        UUID actorTenantId = UUID.randomUUID();
        UUID uploaderId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();

        when(fileSupport.resolveActor(actorUserId, actorTenantId)).thenReturn(actor(actorUserId, actorTenantId));

        File file = file(fileId, actorTenantId, uploaderId);
        when(fileSupport.findReadableFile(fileId, actorTenantId, RoleCode.MEMBER)).thenReturn(file);

        var query = new GetFileMetadataUseCase.GetFileMetadataQuery(actorUserId, actorTenantId, RoleCode.MEMBER, fileId);

        AppException ex = assertThrows(AppException.class, () -> service.execute(query));
        assertEquals(ErrorCode.FORBIDDEN, ex.getError());
    }

    @Test
    void superAdminCanReadAnyFile() {
        UUID actorUserId = UUID.randomUUID();
        UUID actorTenantId = UUID.randomUUID();
        UUID uploaderId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        UUID targetTenantId = UUID.randomUUID();

        when(fileSupport.resolveActor(actorUserId, actorTenantId)).thenReturn(actor(actorUserId, actorTenantId));

        File file = file(fileId, targetTenantId, uploaderId);
        when(fileSupport.findReadableFile(fileId, actorTenantId, RoleCode.SUPER_ADMIN)).thenReturn(file);

        FileView mapped = new FileView(fileId, "a.pdf", "application/pdf", 10L, FileStatus.ACTIVE, null, targetTenantId, "k", uploaderId, "Uploader", null, null, false, null, null, null, null);
        when(fileViewMapper.toFileView(file)).thenReturn(mapped);

        var query = new GetFileMetadataUseCase.GetFileMetadataQuery(actorUserId, actorTenantId, RoleCode.SUPER_ADMIN, fileId);
        var result = service.execute(query);

        assertEquals(fileId, result.id());
        assertEquals(targetTenantId, result.tenantId());
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

    private File file(UUID fileId, UUID tenantId, UUID uploadedById) {
        Tenant tenant = Tenant.builder().name("Tenant").slug("tenant").isActive(true).build();
        tenant.setId(tenantId);

        User uploader = User.builder()
                .tenant(tenant)
                .email("uploader@example.com")
                .passwordHash("hash")
                .fullName("Uploader")
                .build();
        uploader.setId(uploadedById);

        File file = File.builder()
                .tenant(tenant)
                .uploadedBy(uploader)
                .originalName("a.pdf")
                .storageKey("storage")
                .sizeBytes(10L)
                .status(FileStatus.ACTIVE)
                .isIndexed(false)
                .build();
        file.setId(fileId);
        return file;
    }
}

