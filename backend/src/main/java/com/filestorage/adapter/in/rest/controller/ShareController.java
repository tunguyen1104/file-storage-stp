package com.filestorage.adapter.in.rest.controller;

import com.filestorage.adapter.in.rest.dto.request.AdminBulkRevokeRequest;
import com.filestorage.adapter.in.rest.dto.request.RenewShareExpirationRequest;
import com.filestorage.adapter.in.rest.dto.request.ShareFileRequest;
import com.filestorage.adapter.in.rest.dto.request.ShareFolderRequest;
import com.filestorage.adapter.in.rest.dto.request.ShareWithTenantRequest;
import com.filestorage.adapter.in.rest.dto.response.ApiResponse;
import com.filestorage.adapter.in.rest.security.SecurityUser;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.enums.ShareStatus;
import com.filestorage.domain.model.share.AdminBulkRevokeResultView;
import com.filestorage.domain.model.share.ShareExpirationView;
import com.filestorage.domain.model.share.SharePageView;
import com.filestorage.domain.model.share.ShareView;
import com.filestorage.domain.model.share.SharedWithMeView;
import com.filestorage.domain.port.in.share.AdminRevokeShareUseCase;
import com.filestorage.domain.port.in.share.AdminRevokeUserSharesUseCase;
import com.filestorage.domain.port.in.share.GetMySharesQuery;
import com.filestorage.domain.port.in.share.GetMySharesUseCase;
import com.filestorage.domain.port.in.share.GetSharesWithMeQuery;
import com.filestorage.domain.port.in.share.GetSharesWithMeUseCase;
import com.filestorage.domain.port.in.share.RenewShareExpirationUseCase;
import com.filestorage.domain.port.in.share.RevokeShareUseCase;
import com.filestorage.domain.port.in.share.ShareFileCommand;
import com.filestorage.domain.port.in.share.ShareFileUseCase;
import com.filestorage.domain.port.in.share.ShareFileWithTenantUseCase;
import com.filestorage.domain.port.in.share.ShareFolderCommand;
import com.filestorage.domain.port.in.share.ShareFolderUseCase;
import com.filestorage.domain.port.in.share.ShareFolderWithTenantUseCase;
import com.filestorage.domain.port.in.share.ShareWithTenantCommand;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import com.filestorage.shared.util.SecurityContextUtil;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/shares")
@RequiredArgsConstructor
public class ShareController {

    private final ShareFileUseCase shareFileUseCase;
    private final ShareFolderUseCase shareFolderUseCase;
    private final ShareFileWithTenantUseCase shareFileWithTenantUseCase;
    private final ShareFolderWithTenantUseCase shareFolderWithTenantUseCase;
    private final RenewShareExpirationUseCase renewShareExpirationUseCase;
    private final GetMySharesUseCase getMySharesUseCase;
    private final GetSharesWithMeUseCase getSharesWithMeUseCase;
    private final RevokeShareUseCase revokeShareUseCase;
    private final AdminRevokeShareUseCase adminRevokeShareUseCase;
    private final AdminRevokeUserSharesUseCase adminRevokeUserSharesUseCase;

    @PostMapping("/files")
    public ResponseEntity<ApiResponse<ShareView>> shareFile(@RequestBody @Valid ShareFileRequest request) {
        SecurityUser actor = currentUserOrThrow();
        ShareView view = shareFileUseCase.shareFile(new ShareFileCommand(
                actorId(actor),
                tenantId(actor),
                role(actor),
                request.fileId(),
                request.sharedWithUserEmail(),
                resolveExpiresAt(request.expireTime())
        ));
        return ResponseEntity.status(201).body(ApiResponse.buildSuccess(view));
    }

    @PostMapping("/folders")
    public ResponseEntity<ApiResponse<ShareView>> shareFolder(@RequestBody @Valid ShareFolderRequest request) {
        SecurityUser actor = currentUserOrThrow();
        ShareView view = shareFolderUseCase.shareFolder(new ShareFolderCommand(
                actorId(actor),
                tenantId(actor),
                role(actor),
                request.folderId(),
                request.sharedWithUserEmail(),
                request.permission(),
                resolveExpiresAt(request.expireTime())
        ));
        return ResponseEntity.status(201).body(ApiResponse.buildSuccess(view));
    }

    @PostMapping("/files/tenant")
    public ResponseEntity<ApiResponse<ShareView>> shareFileWithTenant(@RequestBody @Valid ShareWithTenantRequest request) {
        SecurityUser actor = currentUserOrThrow();
        ShareView view = shareFileWithTenantUseCase.shareFileWithTenant(new ShareWithTenantCommand(
                actorId(actor),
                tenantId(actor),
                role(actor),
                request.resourceId(),
                resolveExpiresAt(request.expireTime())
        ));
        return ResponseEntity.status(201).body(ApiResponse.buildSuccess(view));
    }

    @PostMapping("/folders/tenant")
    public ResponseEntity<ApiResponse<ShareView>> shareFolderWithTenant(@RequestBody @Valid ShareWithTenantRequest request) {
        SecurityUser actor = currentUserOrThrow();
        ShareView view = shareFolderWithTenantUseCase.shareFolderWithTenant(new ShareWithTenantCommand(
                actorId(actor),
                tenantId(actor),
                role(actor),
                request.resourceId(),
                resolveExpiresAt(request.expireTime())
        ));
        return ResponseEntity.status(201).body(ApiResponse.buildSuccess(view));
    }

    @PatchMapping("/{shareId}/expiration")
    public ResponseEntity<ApiResponse<ShareExpirationView>> renewExpiration(
            @PathVariable UUID shareId,
            @RequestBody @Valid RenewShareExpirationRequest request
    ) {
        SecurityUser actor = currentUserOrThrow();
        ShareExpirationView view = renewShareExpirationUseCase.renew(
                shareId,
                actorId(actor),
                resolveExpiresAt(request.expireTime())
        );
        return ResponseEntity.ok(ApiResponse.buildSuccess(view));
    }

    @GetMapping("/by-me")
    public ResponseEntity<ApiResponse<SharePageView>> getSharesByMe(
            @RequestParam(required = false) String resourceType,
            @RequestParam(required = false) ShareStatus status,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit
    ) {
        SecurityUser actor = currentUserOrThrow();
        SharePageView view = getMySharesUseCase.getMyShares(new GetMySharesQuery(
                actorId(actor),
                resourceType,
                status,
                page,
                limit
        ));
        return ResponseEntity.ok(ApiResponse.buildSuccess(view));
    }

    @GetMapping("/with-me")
    public ResponseEntity<ApiResponse<Page<SharedWithMeView>>> getSharesWithMe(
            @RequestParam(required = false) String resourceType,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int limit
    ) {
        SecurityUser actor = currentUserOrThrow();
        Page<SharedWithMeView> view = getSharesWithMeUseCase.getSharesWithMe(new GetSharesWithMeQuery(
                actorId(actor),
                tenantId(actor),
                resourceType,
                page,
                limit
        ));
        return ResponseEntity.ok(ApiResponse.buildSuccess(view));
    }

    @DeleteMapping("/{shareId}")
    public ResponseEntity<Void> revokeShare(@PathVariable UUID shareId) {
        SecurityUser actor = currentUserOrThrow();
        revokeShareUseCase.revokeShare(shareId, actorId(actor));
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/admin/{shareId}")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<Void> adminRevokeShare(@PathVariable UUID shareId) {
        SecurityUser actor = currentUserOrThrow();
        adminRevokeShareUseCase.adminRevokeShare(shareId, actorId(actor));
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/admin/users/{userId}/revoke")
    @PreAuthorize("hasAnyRole('TENANT_ADMIN', 'SUPER_ADMIN')")
    public ResponseEntity<ApiResponse<AdminBulkRevokeResultView>> adminRevokeUserShares(
            @PathVariable UUID userId,
            @RequestBody @Valid AdminBulkRevokeRequest request
    ) {
        SecurityUser actor = currentUserOrThrow();
        AdminBulkRevokeResultView view = adminRevokeUserSharesUseCase.adminRevokeUserShares(
                userId,
                actorId(actor),
                request.scope().name()
        );
        return ResponseEntity.ok(ApiResponse.buildSuccess(view));
    }

    private SecurityUser currentUserOrThrow() {
        return SecurityContextUtil.getCurrentUser().orElseThrow(() -> new AppException(ErrorCode.ACCESS_DENIED));
    }

    private UUID actorId(SecurityUser actor) {
        return UUID.fromString(actor.getUserId());
    }

    private UUID tenantId(SecurityUser actor) {
        return UUID.fromString(actor.getTenantId());
    }

    private RoleCode role(SecurityUser actor) {
        try {
            return RoleCode.valueOf(actor.getRole());
        } catch (IllegalArgumentException ex) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
    }

    private Instant resolveExpiresAt(Long expireTimeMinutes) {
        if (expireTimeMinutes == null || expireTimeMinutes <= 0) {
            throw new AppException(ErrorCode.EXPIRES_AT_IN_PAST);
        }
        return Instant.now().plus(Duration.ofMinutes(expireTimeMinutes));
    }
}
