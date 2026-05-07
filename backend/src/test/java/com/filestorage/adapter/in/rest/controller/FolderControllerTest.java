package com.filestorage.adapter.in.rest.controller;

import com.filestorage.adapter.in.rest.advice.GlobalExceptionHandler;
import com.filestorage.adapter.in.rest.dto.response.AdminFolderItemResponse;
import com.filestorage.adapter.in.rest.dto.response.PageResponse;
import com.filestorage.adapter.in.rest.dto.response.UserSummaryResponse;
import com.filestorage.adapter.in.rest.mapper.FolderRestMapper;
import com.filestorage.adapter.in.rest.security.SecurityUser;
import com.filestorage.domain.model.enums.AdminFolderItemSortBy;
import com.filestorage.domain.model.enums.FileStatus;
import com.filestorage.domain.model.enums.FolderItemType;
import com.filestorage.domain.model.folder.AdminFolderItemPageView;
import com.filestorage.domain.model.folder.AdminFolderItemView;
import com.filestorage.domain.port.in.folder.CreateFolderUseCase;
import com.filestorage.domain.port.in.folder.DeleteFolderUseCase;
import com.filestorage.domain.port.in.folder.GetFolderBreadcrumbsUseCase;
import com.filestorage.domain.port.in.folder.GetFolderUseCase;
import com.filestorage.domain.port.in.folder.ListAllTenantFolderItemsUseCase;
import com.filestorage.domain.port.in.folder.ListFolderChildrenUseCase;
import com.filestorage.domain.port.in.folder.ListRootItemsUseCase;
import com.filestorage.domain.port.in.folder.MoveFolderUseCase;
import com.filestorage.domain.port.in.folder.RenameFolderUseCase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class FolderControllerTest {

    private MockMvc mockMvc;

    @Mock
    private CreateFolderUseCase createFolderUseCase;
    @Mock
    private GetFolderUseCase getFolderUseCase;
    @Mock
    private GetFolderBreadcrumbsUseCase getFolderBreadcrumbsUseCase;
    @Mock
    private ListAllTenantFolderItemsUseCase listAllTenantFolderItemsUseCase;
    @Mock
    private ListFolderChildrenUseCase listFolderChildrenUseCase;
    @Mock
    private ListRootItemsUseCase listRootItemsUseCase;
    @Mock
    private RenameFolderUseCase renameFolderUseCase;
    @Mock
    private MoveFolderUseCase moveFolderUseCase;
    @Mock
    private DeleteFolderUseCase deleteFolderUseCase;
    @Mock
    private FolderRestMapper folderRestMapper;

    private UUID actorUserId;
    private UUID actorTenantId;

    @BeforeEach
    void setUp() {
        actorUserId = UUID.randomUUID();
        actorTenantId = UUID.randomUUID();
        SecurityUser securityUser = new SecurityUser(
                actorUserId.toString(), actorTenantId.toString(), "SUPER_ADMIN", "super.admin@example.com");
        var auth = new UsernamePasswordAuthenticationToken(securityUser, null, securityUser.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);

        var messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");

        var validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(new FolderController(
                        createFolderUseCase,
                        getFolderUseCase,
                        getFolderBreadcrumbsUseCase,
                        listAllTenantFolderItemsUseCase,
                        listFolderChildrenUseCase,
                        listRootItemsUseCase,
                        renameFolderUseCase,
                        moveFolderUseCase,
                        deleteFolderUseCase,
                        folderRestMapper
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
    void listAdminItems_returns200() throws Exception {
        UUID tenantId = UUID.randomUUID();
        UUID parentId = UUID.randomUUID();
        UUID creatorId = UUID.randomUUID();
        UUID itemId = UUID.randomUUID();
        Instant now = Instant.now();

        AdminFolderItemView item = new AdminFolderItemView(
                itemId,
                FolderItemType.FILE,
                "document.pdf",
                tenantId,
                "Tenant A",
                parentId,
                "Docs",
                12L,
                "application/pdf",
                FileStatus.ACTIVE,
                creatorId,
                "Creator",
                now,
                now
        );
        AdminFolderItemPageView pageView = new AdminFolderItemPageView(List.of(item), 1, 20, 1L, 1);
        PageResponse<AdminFolderItemResponse> pageResponse = PageResponse.<AdminFolderItemResponse>builder()
                .data(List.of(new AdminFolderItemResponse(
                        itemId,
                        "file",
                        "document.pdf",
                        tenantId,
                        "Tenant A",
                        parentId,
                        "Docs",
                        12L,
                        "application/pdf",
                        FileStatus.ACTIVE,
                        new UserSummaryResponse(creatorId, "Creator"),
                        now,
                        now
                )))
                .currentPage(1)
                .pageSize(20)
                .totalElements(1)
                .totalPages(1)
                .build();

        when(listAllTenantFolderItemsUseCase.execute(argThat(query ->
                query.actorUserId().equals(actorUserId)
                        && query.actorTenantId().equals(actorTenantId)
                        && query.tenantId().equals(tenantId)
                        && query.type() == FolderItemType.FILE
                        && query.parentId().equals(parentId)
                        && query.status() == FileStatus.ACTIVE
                        && query.createdBy().equals(creatorId)
                        && query.search().equals("doc")
                        && query.page() == 1
                        && query.limit() == 20
                        && query.sortBy() == AdminFolderItemSortBy.NAME
        ))).thenReturn(pageView);
        when(folderRestMapper.toAdminItemPageResponse(pageView)).thenReturn(pageResponse);

        mockMvc.perform(get("/api/v1/folders/admin/items")
                        .param("tenantId", tenantId.toString())
                        .param("type", "FILE")
                        .param("parentId", parentId.toString())
                        .param("status", "ACTIVE")
                        .param("createdBy", creatorId.toString())
                        .param("search", "doc")
                        .param("page", "1")
                        .param("limit", "20")
                        .param("sortBy", "NAME"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.totalElements").value(1))
                .andExpect(jsonPath("$.data.data[0].type").value("file"))
                .andExpect(jsonPath("$.data.data[0].tenantName").value("Tenant A"))
                .andExpect(jsonPath("$.data.data[0].actor.fullName").value("Creator"));
    }
}
