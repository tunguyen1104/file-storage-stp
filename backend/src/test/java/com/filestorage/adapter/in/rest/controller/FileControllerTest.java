package com.filestorage.adapter.in.rest.controller;

import com.filestorage.adapter.in.rest.advice.GlobalExceptionHandler;
import com.filestorage.adapter.in.rest.dto.response.FileMetadataResponse;
import com.filestorage.adapter.in.rest.dto.response.FileResponse;
import com.filestorage.adapter.in.rest.dto.response.PageResponse;
import com.filestorage.adapter.in.rest.dto.response.UserSummaryResponse;
import com.filestorage.adapter.in.rest.mapper.FileRestMapper;
import com.filestorage.adapter.in.rest.security.SecurityUser;
import com.filestorage.domain.model.enums.FileStatus;
import com.filestorage.domain.model.file.FileDownloadView;
import com.filestorage.domain.model.file.FilePageView;
import com.filestorage.domain.model.file.FilePresignedGetView;
import com.filestorage.domain.model.file.FileView;
import com.filestorage.domain.port.in.file.BulkUploadFilesUseCase;
import com.filestorage.domain.port.in.file.DeleteFileUseCase;
import com.filestorage.domain.port.in.file.DownloadFileUseCase;
import com.filestorage.domain.port.in.file.GetFileMetadataUseCase;
import com.filestorage.domain.port.in.file.GetFilePresignedUrlUseCase;
import com.filestorage.domain.port.in.file.ListFileMetadataUseCase;
import com.filestorage.domain.port.in.file.RestoreFileUseCase;
import com.filestorage.domain.port.in.file.UploadFileUseCase;
import com.filestorage.domain.port.in.file.DirectDeleteFileUseCase;
import com.filestorage.domain.port.in.file.RenameFileUseCase;
import com.filestorage.domain.port.in.file.MoveFileUseCase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class FileControllerTest {

    private MockMvc mockMvc;

    @Mock
    private UploadFileUseCase uploadFileUseCase;
    @Mock
    private BulkUploadFilesUseCase bulkUploadFilesUseCase;
    @Mock
    private GetFileMetadataUseCase getFileMetadataUseCase;
    @Mock
    private ListFileMetadataUseCase listFileMetadataUseCase;
    @Mock
    private DownloadFileUseCase downloadFileUseCase;
    @Mock
    private GetFilePresignedUrlUseCase getFilePresignedUrlUseCase;
    @Mock
    private DeleteFileUseCase deleteFileUseCase;
    @Mock
    private DirectDeleteFileUseCase directDeleteFileUseCase;
    @Mock
    private RestoreFileUseCase restoreFileUseCase;
    @Mock
    private RenameFileUseCase renameFileUseCase;
    @Mock
    private MoveFileUseCase moveFileUseCase;
    @Mock
    private FileRestMapper fileRestMapper;

    @BeforeEach
    void setUp() {
        UUID actorUserId = UUID.randomUUID();
        UUID actorTenantId = UUID.randomUUID();
        SecurityUser securityUser = new SecurityUser(actorUserId.toString(), actorTenantId.toString(), "TENANT_ADMIN", "admin@example.com");
        var auth = new UsernamePasswordAuthenticationToken(securityUser, null, securityUser.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        var messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");

        var validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        when(fileRestMapper.toFileResponse(any())).thenAnswer(invocation -> {
            FileView view = invocation.getArgument(0);
            return new FileResponse(
                    view.id(),
                    view.originalName(),
                    view.mimeType(),
                    view.sizeBytes(),
                    view.status(),
                    view.folderId(),
                    view.tenantId(),
                    view.storageKey(),
                    view.checksumMd5(),
                    view.thumbnailKey(),
                    view.isIndexed(),
                    new UserSummaryResponse(view.uploadedById(), view.uploadedByFullName()),
                    view.deletedAt(),
                    view.scheduledDeleteAt(),
                    view.createdAt(),
                    view.updatedAt()
            );
        });

        when(fileRestMapper.toPageResponse(any())).thenAnswer(invocation -> {
            FilePageView view = invocation.getArgument(0);
            return PageResponse.<FileResponse>builder()
                    .data(view.data().stream().map(fileRestMapper::toFileResponse).toList())
                    .currentPage(view.page())
                    .pageSize(view.limit())
                    .totalElements(view.total())
                    .totalPages(view.totalPages())
                    .build();
        });

        when(fileRestMapper.toFileMetadataResponse(any())).thenAnswer(invocation -> {
            FileView view = invocation.getArgument(0);
            return new FileMetadataResponse(
                    view.id(),
                    view.originalName(),
                    view.mimeType(),
                    view.sizeBytes(),
                    view.status(),
                    view.folderId(),
                    view.tenantId(),
                    view.checksumMd5(),
                    view.thumbnailKey(),
                    view.isIndexed(),
                    new UserSummaryResponse(view.uploadedById(), view.uploadedByFullName()),
                    view.deletedAt(),
                    view.scheduledDeleteAt(),
                    view.createdAt(),
                    view.updatedAt()
            );
        });

        when(fileRestMapper.toMetadataPageResponse(any())).thenAnswer(invocation -> {
            FilePageView view = invocation.getArgument(0);
            return PageResponse.<FileMetadataResponse>builder()
                    .data(view.data().stream().map(fileRestMapper::toFileMetadataResponse).toList())
                    .currentPage(view.page())
                    .pageSize(view.limit())
                    .totalElements(view.total())
                    .totalPages(view.totalPages())
                    .build();
        });

        mockMvc = MockMvcBuilders.standaloneSetup(new FileController(
                        uploadFileUseCase,
                        bulkUploadFilesUseCase,
                        getFileMetadataUseCase,
                        listFileMetadataUseCase,
                        downloadFileUseCase,
                        getFilePresignedUrlUseCase,
                        deleteFileUseCase,
                        directDeleteFileUseCase,
                        restoreFileUseCase,
                        renameFileUseCase,
                        moveFileUseCase,
                        fileRestMapper
                ))
                .setControllerAdvice(new GlobalExceptionHandler(messageSource))
                .setValidator(validator)
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void uploadSingle_returns201() throws Exception {
        UUID fileId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        FileView view = new FileView(fileId, "photo.jpg", "image/jpeg", 3L, FileStatus.ACTIVE, null, tenantId, "tenant/user/file.jpg", userId, "Uploader", null, null, false, null, null, Instant.now(), Instant.now());
        when(uploadFileUseCase.execute(any())).thenReturn(view);

        MockMultipartFile file = new MockMultipartFile("file", "photo.jpg", "image/jpeg", "abc".getBytes());
        mockMvc.perform(multipart("/api/v1/uploads/single").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.originalName").value("photo.jpg"));
    }

    @Test
    void uploadSingle_usesGeneratedNameWhenMultipartFilenameMissing() throws Exception {
        UUID fileId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        when(uploadFileUseCase.execute(argThat(cmd -> cmd.originalName() != null && cmd.originalName().startsWith("upload-"))))
                .thenReturn(new FileView(fileId, "upload-generated", "application/octet-stream", 3L, FileStatus.ACTIVE, null, tenantId, "tenant/user/file", userId, "Uploader", null, null, false, null, null, Instant.now(), Instant.now()));

        MockMultipartFile file = new MockMultipartFile("file", "", "application/octet-stream", "abc".getBytes());
        mockMvc.perform(multipart("/api/v1/uploads/single").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true));
    }

    @Test
    void uploadBulk_returns201() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        FileView first = new FileView(UUID.randomUUID(), "one.txt", "text/plain", 1L, FileStatus.ACTIVE, null, tenantId, "tenant/user/one.txt", userId, "Uploader", null, null, false, null, null, Instant.now(), Instant.now());
        FileView second = new FileView(UUID.randomUUID(), "two.txt", "text/plain", 1L, FileStatus.ACTIVE, null, tenantId, "tenant/user/two.txt", userId, "Uploader", null, null, false, null, null, Instant.now(), Instant.now());
        when(bulkUploadFilesUseCase.execute(any())).thenReturn(List.of(first, second));

        MockMultipartFile firstFile = new MockMultipartFile("files", "one.txt", "text/plain", "a".getBytes());
        MockMultipartFile secondFile = new MockMultipartFile("files", "two.txt", "text/plain", "b".getBytes());
        mockMvc.perform(multipart("/api/v1/uploads/bulk").file(firstFile).file(secondFile))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.length()").value(2));
    }

    @Test
    void download_returns200() throws Exception {
        when(downloadFileUseCase.execute(any())).thenReturn(new FileDownloadView("https://example.com", Instant.now().plusSeconds(900), "photo.jpg", "image/jpeg", 3L));

        mockMvc.perform(get("/api/v1/files/{fileId}/download", UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.downloadUrl").value("https://example.com"));
    }

    @Test
    void getPresignedGetUrl_returns200() throws Exception {
        when(getFilePresignedUrlUseCase.execute(any()))
                .thenReturn(new FilePresignedGetView("https://example.com/open", Instant.now().plusSeconds(900), "photo.jpg", "image/jpeg", 3L));

        mockMvc.perform(get("/api/v1/files/{fileId}/presigned-get-url", UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.getUrl").value("https://example.com/open"));
    }

    @Test
    void getFileMetadata_returns200() throws Exception {
        UUID fileId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        FileView view = new FileView(fileId, "photo.jpg", "image/jpeg", 3L, FileStatus.ACTIVE, null, tenantId, "tenant/user/file.jpg", userId, "Uploader", null, null, false, null, null, Instant.now(), Instant.now());
        when(getFileMetadataUseCase.execute(any())).thenReturn(view);

        mockMvc.perform(get("/api/v1/files/{fileId}", fileId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(fileId.toString()))
                .andExpect(jsonPath("$.data.storageKey").doesNotExist());
    }

    @Test
    void listFileMetadata_returnsPagedResponse() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        FileView item = new FileView(UUID.randomUUID(), "photo.jpg", "image/jpeg", 3L, FileStatus.ACTIVE, null, tenantId, "tenant/user/file.jpg", userId, "Uploader", null, null, false, null, null, Instant.now(), Instant.now());
        when(listFileMetadataUseCase.execute(any())).thenReturn(new FilePageView(java.util.List.of(item), 1, 20, 1L, 1));

        mockMvc.perform(get("/api/v1/files"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.data[0].originalName").value("photo.jpg"))
                .andExpect(jsonPath("$.data.data[0].storageKey").doesNotExist());
    }

    @Test
    void delete_returns200() throws Exception {
        UUID fileId = UUID.randomUUID();
        FileView view = new FileView(fileId, "photo.jpg", "image/jpeg", 3L, FileStatus.PENDING_DELETE, null, UUID.randomUUID(), "tenant/user/file.jpg", UUID.randomUUID(), "Uploader", null, null, false, Instant.now(), Instant.now().plusSeconds(30), Instant.now(), Instant.now());
        when(deleteFileUseCase.execute(any())).thenReturn(view);

        mockMvc.perform(delete("/api/v1/files/{fileId}", fileId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("PENDING_DELETE"));
    }


    @Test
    void restore_returns200() throws Exception {
        UUID fileId = UUID.randomUUID();
        FileView view = new FileView(fileId, "photo.jpg", "image/jpeg", 3L, FileStatus.ACTIVE, null, UUID.randomUUID(), "tenant/user/file.jpg", UUID.randomUUID(), "Uploader", null, null, false, null, null, Instant.now(), Instant.now());
        when(restoreFileUseCase.execute(any())).thenReturn(view);

        mockMvc.perform(post("/api/v1/files/{fileId}/restore", fileId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("ACTIVE"));
    }

    @Test
    void rename_returns200() throws Exception {
        UUID fileId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        FileView view = new FileView(fileId, "renamed.pdf", "application/pdf", 3L, FileStatus.ACTIVE, null, tenantId,
                "tenant/user/file.pdf", userId, "Uploader", null, null, false, null, null, Instant.now(), Instant.now());
        when(renameFileUseCase.execute(any())).thenReturn(view);

        mockMvc.perform(patch("/api/v1/files/{fileId}", fileId)
                        .contentType("application/json")
                        .content("{\"originalName\":\"renamed.pdf\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.originalName").value("renamed.pdf"));
    }
}
