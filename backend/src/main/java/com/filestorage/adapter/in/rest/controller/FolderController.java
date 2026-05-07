package com.filestorage.adapter.in.rest.controller;

import com.filestorage.adapter.in.rest.dto.request.CreateFolderRequest;
import com.filestorage.adapter.in.rest.dto.request.MoveFolderRequest;
import com.filestorage.adapter.in.rest.dto.request.RenameFolderRequest;
import com.filestorage.adapter.in.rest.dto.response.AdminFolderItemResponse;
import com.filestorage.adapter.in.rest.dto.response.ApiResponse;
import com.filestorage.adapter.in.rest.dto.response.BreadcrumbResponse;
import com.filestorage.adapter.in.rest.dto.response.FolderItemResponse;
import com.filestorage.adapter.in.rest.dto.response.FolderResponse;
import com.filestorage.adapter.in.rest.dto.response.PageResponse;
import com.filestorage.adapter.in.rest.mapper.FolderRestMapper;
import com.filestorage.adapter.in.rest.security.SecurityUser;
import com.filestorage.domain.model.enums.AdminFolderItemSortBy;
import com.filestorage.domain.model.enums.FileStatus;
import com.filestorage.domain.model.enums.FolderItemType;
import com.filestorage.domain.model.enums.FolderSortBy;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.enums.SortDirection;
import com.filestorage.domain.model.folder.AdminFolderItemPageView;
import com.filestorage.domain.model.folder.FolderItemPageView;
import com.filestorage.domain.model.folder.FolderView;
import com.filestorage.domain.port.in.folder.CreateFolderUseCase;
import com.filestorage.domain.port.in.folder.DeleteFolderUseCase;
import com.filestorage.domain.port.in.folder.GetFolderBreadcrumbsUseCase;
import com.filestorage.domain.port.in.folder.GetFolderUseCase;
import com.filestorage.domain.port.in.folder.ListAllTenantFolderItemsUseCase;
import com.filestorage.domain.port.in.folder.ListFolderChildrenUseCase;
import com.filestorage.domain.port.in.folder.ListRootItemsUseCase;
import com.filestorage.domain.port.in.folder.MoveFolderUseCase;
import com.filestorage.domain.port.in.folder.RenameFolderUseCase;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import com.filestorage.shared.util.SecurityContextUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/folders")
@RequiredArgsConstructor
public class FolderController {

    private final CreateFolderUseCase createFolderUseCase;
    private final GetFolderUseCase getFolderUseCase;
    private final GetFolderBreadcrumbsUseCase getFolderBreadcrumbsUseCase;
    private final ListAllTenantFolderItemsUseCase listAllTenantFolderItemsUseCase;
    private final ListFolderChildrenUseCase listFolderChildrenUseCase;
    private final ListRootItemsUseCase listRootItemsUseCase;
    private final RenameFolderUseCase renameFolderUseCase;
    private final MoveFolderUseCase moveFolderUseCase;
    private final DeleteFolderUseCase deleteFolderUseCase;
    private final FolderRestMapper folderRestMapper;

    @PostMapping
    public ResponseEntity<ApiResponse<FolderResponse>> createFolder(@RequestBody @Valid CreateFolderRequest request) {
        SecurityUser actor = currentUserOrThrow();
        FolderView view = createFolderUseCase.execute(new CreateFolderUseCase.CreateFolderCommand(
                UUID.fromString(actor.getUserId()),
                UUID.fromString(actor.getTenantId()),
                parseRole(actor.getRole()),
                request.name(),
                request.parentId()
        ));

        return ResponseEntity.status(201).body(ApiResponse.buildSuccess(folderRestMapper.toFolderResponse(view)));
    }

    @GetMapping("/{folderId}")
    public ResponseEntity<ApiResponse<FolderResponse>> getFolder(@PathVariable UUID folderId) {
        SecurityUser actor = currentUserOrThrow();
        FolderView view = getFolderUseCase.execute(new GetFolderUseCase.GetFolderQuery(
                UUID.fromString(actor.getUserId()),
                UUID.fromString(actor.getTenantId()),
                parseRole(actor.getRole()),
                folderId
        ));

        return ResponseEntity.ok(ApiResponse.buildSuccess(folderRestMapper.toFolderResponse(view)));
    }

    @GetMapping("/{folderId}/breadcrumbs")
    public ResponseEntity<ApiResponse<List<BreadcrumbResponse>>> getBreadcrumbs(@PathVariable UUID folderId) {
        SecurityUser actor = currentUserOrThrow();
        List<BreadcrumbResponse> data = getFolderBreadcrumbsUseCase.execute(new GetFolderBreadcrumbsUseCase.GetFolderBreadcrumbsQuery(
                UUID.fromString(actor.getUserId()),
                UUID.fromString(actor.getTenantId()),
                parseRole(actor.getRole()),
                folderId
        )).stream().map(folderRestMapper::toBreadcrumbResponse).toList();

        return ResponseEntity.ok(ApiResponse.buildSuccess(data));
    }

    @GetMapping("/{folderId}/children")
    public ResponseEntity<ApiResponse<PageResponse<FolderItemResponse>>> listChildren(
            @PathVariable UUID folderId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) FolderSortBy sort,
            @RequestParam(required = false) SortDirection sortOrder,
            @RequestParam(required = false) FolderItemType type,
            @RequestParam(required = false) String search
    ) {
        SecurityUser actor = currentUserOrThrow();
        FolderItemPageView view = listFolderChildrenUseCase.execute(new ListFolderChildrenUseCase.ListFolderChildrenQuery(
                UUID.fromString(actor.getUserId()),
                UUID.fromString(actor.getTenantId()),
                parseRole(actor.getRole()),
                folderId,
                page,
                size,
                sort,
                sortOrder,
                type,
                search
        ));

        return ResponseEntity.ok(ApiResponse.buildSuccess(folderRestMapper.toItemPageResponse(view)));
    }

    @GetMapping("/admin/items")
    @PreAuthorize("hasRole('SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<PageResponse<AdminFolderItemResponse>>> listAdminItems(
            @RequestParam(required = false) UUID tenantId,
            @RequestParam(required = false) FolderItemType type,
            @RequestParam(required = false) UUID parentId,
            @RequestParam(required = false) FileStatus status,
            @RequestParam(required = false) UUID createdBy,
            @RequestParam(required = false) String search,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit,
            @RequestParam(required = false) AdminFolderItemSortBy sortBy,
            @RequestParam(required = false) SortDirection sortDirection
    ) {
        SecurityUser actor = currentUserOrThrow();
        AdminFolderItemPageView view = listAllTenantFolderItemsUseCase.execute(new ListAllTenantFolderItemsUseCase.ListAllTenantFolderItemsQuery(
                UUID.fromString(actor.getUserId()),
                UUID.fromString(actor.getTenantId()),
                parseRole(actor.getRole()),
                tenantId,
                type,
                parentId,
                status,
                createdBy,
                search,
                page,
                limit,
                sortBy,
                sortDirection
        ));

        return ResponseEntity.ok(ApiResponse.buildSuccess(folderRestMapper.toAdminItemPageResponse(view)));
    }

    @GetMapping("/root")
    public ResponseEntity<ApiResponse<PageResponse<FolderItemResponse>>> listRoot(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size,
            @RequestParam(required = false) FolderSortBy sort,
            @RequestParam(required = false) SortDirection sortOrder,
            @RequestParam(required = false) FolderItemType type,
            @RequestParam(required = false) String search
    ) {
        SecurityUser actor = currentUserOrThrow();
        FolderItemPageView view = listRootItemsUseCase.execute(new ListRootItemsUseCase.ListRootItemsQuery(
                UUID.fromString(actor.getUserId()),
                UUID.fromString(actor.getTenantId()),
                parseRole(actor.getRole()),
                page,
                size,
                sort,
                sortOrder,
                type,
                search
        ));

        return ResponseEntity.ok(ApiResponse.buildSuccess(folderRestMapper.toItemPageResponse(view)));
    }

    @PatchMapping("/{folderId}")
    public ResponseEntity<ApiResponse<FolderResponse>> renameFolder(
            @PathVariable UUID folderId,
            @RequestBody @Valid RenameFolderRequest request
    ) {
        SecurityUser actor = currentUserOrThrow();
        FolderView view = renameFolderUseCase.execute(new RenameFolderUseCase.RenameFolderCommand(
                UUID.fromString(actor.getUserId()),
                UUID.fromString(actor.getTenantId()),
                parseRole(actor.getRole()),
                folderId,
                request.name()
        ));

        return ResponseEntity.ok(ApiResponse.buildSuccess(folderRestMapper.toFolderResponse(view)));
    }

    @PatchMapping("/{folderId}/move")
    public ResponseEntity<ApiResponse<FolderResponse>> moveFolder(
            @PathVariable UUID folderId,
            @RequestBody MoveFolderRequest request
    ) {
        SecurityUser actor = currentUserOrThrow();
        FolderView view = moveFolderUseCase.execute(new MoveFolderUseCase.MoveFolderCommand(
                UUID.fromString(actor.getUserId()),
                UUID.fromString(actor.getTenantId()),
                parseRole(actor.getRole()),
                folderId,
                request.parentId()
        ));

        return ResponseEntity.ok(ApiResponse.buildSuccess(folderRestMapper.toFolderResponse(view)));
    }

    @DeleteMapping("/{folderId}")
    public ResponseEntity<Void> deleteFolder(@PathVariable UUID folderId) {
        SecurityUser actor = currentUserOrThrow();
        deleteFolderUseCase.execute(new DeleteFolderUseCase.DeleteFolderCommand(
                UUID.fromString(actor.getUserId()),
                UUID.fromString(actor.getTenantId()),
                parseRole(actor.getRole()),
                folderId
        ));

        return ResponseEntity.noContent().build();
    }

    private SecurityUser currentUserOrThrow() {
        return SecurityContextUtil.getCurrentUser().orElseThrow(() -> new AppException(ErrorCode.ACCESS_DENIED));
    }

    private RoleCode parseRole(String role) {
        try {
            return RoleCode.valueOf(role);
        } catch (IllegalArgumentException ex) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
    }
}
