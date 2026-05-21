package com.filestorage.adapter.in.rest.mapper;

import com.filestorage.adapter.in.rest.dto.response.PageResponse;
import com.filestorage.adapter.in.rest.dto.response.share.AdminBulkRevokeResultResponse;
import com.filestorage.adapter.in.rest.dto.response.share.ShareExpirationResponse;
import com.filestorage.adapter.in.rest.dto.response.share.ShareResourceResponse;
import com.filestorage.adapter.in.rest.dto.response.share.ShareResponse;
import com.filestorage.adapter.in.rest.dto.response.share.ShareTenantResponse;
import com.filestorage.adapter.in.rest.dto.response.share.ShareUserResponse;
import com.filestorage.adapter.in.rest.dto.response.share.ShareWithResourceResponse;
import com.filestorage.adapter.in.rest.dto.response.share.SharedByResponse;
import com.filestorage.adapter.in.rest.dto.response.share.SharedWithMeResponse;
import com.filestorage.domain.model.share.AdminBulkRevokeResultView;
import com.filestorage.domain.model.share.PaginationView;
import com.filestorage.domain.model.share.ShareListPageView;
import com.filestorage.domain.model.share.ShareExpirationView;
import com.filestorage.domain.model.share.SharePageView;
import com.filestorage.domain.model.share.ShareResourceView;
import com.filestorage.domain.model.share.ShareTenantView;
import com.filestorage.domain.model.share.ShareUserView;
import com.filestorage.domain.model.share.ShareView;
import com.filestorage.domain.model.share.ShareWithResourceView;
import com.filestorage.domain.model.share.SharedByView;
import com.filestorage.domain.model.share.SharedWithMePageView;
import com.filestorage.domain.model.share.SharedWithMeView;
import com.filestorage.infrastructure.config.GlobalMapperConfig;
import org.mapstruct.Mapper;

@Mapper(config = GlobalMapperConfig.class)
public interface ShareRestMapper {

    default ShareResponse toShareResponse(ShareView view) {
        if (view == null) {
            return null;
        }
        return new ShareResponse(
                view.id(),
                view.tenantId(),
                view.tenantName(),
                view.resourceType(),
                view.fileId(),
                view.folderId(),
                view.permission(),
                view.status(),
                view.recipientType(),
                toShareUserResponse(view.sharedWithUser()),
                toShareTenantResponse(view.sharedWithTenant()),
                toSharedByResponse(view.sharedBy()),
                view.expiresAt(),
                view.createdAt(),
                view.revokedAt()
        );
    }

    default ShareExpirationResponse toShareExpirationResponse(ShareExpirationView view) {
        if (view == null) {
            return null;
        }
        return new ShareExpirationResponse(
                view.id(),
                view.status(),
                view.expiresAt(),
                view.revokedAt(),
                view.updatedAt()
        );
    }

    default ShareWithResourceResponse toShareWithResourceResponse(ShareWithResourceView view) {
        if (view == null) {
            return null;
        }
        return new ShareWithResourceResponse(
                view.id(),
                view.resourceType(),
                view.fileId(),
                view.folderId(),
                view.permission(),
                view.status(),
                view.recipientType(),
                toShareResourceResponse(view.resource()),
                toShareUserResponse(view.sharedWithUser()),
                toShareTenantResponse(view.sharedWithTenant()),
                view.expiresAt(),
                view.createdAt(),
                view.revokedAt()
        );
    }

    default SharedWithMeResponse toSharedWithMeResponse(SharedWithMeView view) {
        if (view == null) {
            return null;
        }
        return new SharedWithMeResponse(
                view.shareId(),
                view.resourceType(),
                view.permission(),
                view.status(),
                view.recipientType(),
                view.expiresAt(),
                toSharedByResponse(view.sharedBy()),
                toShareResourceResponse(view.resource())
        );
    }

    default AdminBulkRevokeResultResponse toAdminBulkRevokeResultResponse(AdminBulkRevokeResultView view) {
        if (view == null) {
            return null;
        }
        return new AdminBulkRevokeResultResponse(
                view.targetUserId(),
                view.scope(),
                view.revokedShareIds(),
                view.revokedCount(),
                view.auditId()
        );
    }

    default PageResponse<ShareWithResourceResponse> toShareWithResourcePageResponse(SharePageView pageView) {
        if (pageView == null) {
            return null;
        }
        PaginationView pagination = pageView.pagination();
        return PageResponse.<ShareWithResourceResponse>builder()
                .data(pageView.data().stream().map(this::toShareWithResourceResponse).toList())
                .currentPage(pagination.page())
                .pageSize(pagination.limit())
                .totalElements(pagination.total())
                .totalPages(pagination.totalPages())
                .build();
    }

    default PageResponse<ShareResponse> toSharePageResponse(ShareListPageView pageView) {
        if (pageView == null) {
            return null;
        }
        PaginationView pagination = pageView.pagination();
        return PageResponse.<ShareResponse>builder()
                .data(pageView.data().stream().map(this::toShareResponse).toList())
                .currentPage(pagination.page())
                .pageSize(pagination.limit())
                .totalElements(pagination.total())
                .totalPages(pagination.totalPages())
                .build();
    }

    default PageResponse<SharedWithMeResponse> toSharedWithMePageResponse(SharedWithMePageView pageView) {
        if (pageView == null) {
            return null;
        }
        PaginationView pagination = pageView.pagination();
        return PageResponse.<SharedWithMeResponse>builder()
                .data(pageView.data().stream().map(this::toSharedWithMeResponse).toList())
                .currentPage(pagination.page())
                .pageSize(pagination.limit())
                .totalElements(pagination.total())
                .totalPages(pagination.totalPages())
                .build();
    }

    default ShareUserResponse toShareUserResponse(ShareUserView view) {
        if (view == null) {
            return null;
        }
        return new ShareUserResponse(view.id(), view.email(), view.fullName());
    }

    default ShareTenantResponse toShareTenantResponse(ShareTenantView view) {
        if (view == null) {
            return null;
        }
        return new ShareTenantResponse(view.id(), view.name());
    }

    default SharedByResponse toSharedByResponse(SharedByView view) {
        if (view == null) {
            return null;
        }
        return new SharedByResponse(view.id(), view.fullName());
    }

    default ShareResourceResponse toShareResourceResponse(ShareResourceView view) {
        if (view == null) {
            return null;
        }
        return new ShareResourceResponse(
                view.type(),
                view.id(),
                view.name(),
                view.mimeType(),
                view.sizeBytes()
        );
    }
}
