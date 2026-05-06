package com.filestorage.adapter.in.rest.mapper;

import com.filestorage.adapter.in.rest.dto.response.AdminFolderItemResponse;
import com.filestorage.adapter.in.rest.dto.response.BreadcrumbResponse;
import com.filestorage.adapter.in.rest.dto.response.FolderItemResponse;
import com.filestorage.adapter.in.rest.dto.response.FolderResponse;
import com.filestorage.adapter.in.rest.dto.response.PageResponse;
import com.filestorage.adapter.in.rest.dto.response.UserSummaryResponse;
import com.filestorage.domain.model.folder.BreadcrumbView;
import com.filestorage.domain.model.folder.AdminFolderItemPageView;
import com.filestorage.domain.model.folder.AdminFolderItemView;
import com.filestorage.domain.model.folder.FolderItemPageView;
import com.filestorage.domain.model.folder.FolderItemView;
import com.filestorage.domain.model.folder.FolderView;
import com.filestorage.infrastructure.config.GlobalMapperConfig;
import org.mapstruct.Mapper;

@Mapper(config = GlobalMapperConfig.class)
public interface FolderRestMapper {

    FolderResponse toFolderResponse(FolderView view);

    BreadcrumbResponse toBreadcrumbResponse(BreadcrumbView view);

    default FolderItemResponse toFolderItemResponse(FolderItemView view) {
        return new FolderItemResponse(
                view.id(),
                view.type() == null ? null : view.type().name().toLowerCase(),
                view.name(),
                view.parentId(),
                view.sizeBytes(),
                view.mimeType(),
                view.status(),
                toUserSummaryResponse(view.actorId(), view.actorFullName()),
                view.createdAt(),
                view.updatedAt()
        );
    }

    default PageResponse<FolderItemResponse> toItemPageResponse(FolderItemPageView pageView) {
        return PageResponse.<FolderItemResponse>builder()
                .data(pageView.data().stream().map(this::toFolderItemResponse).toList())
                .currentPage(pageView.page())
                .pageSize(pageView.size())
                .totalElements(pageView.total())
                .totalPages(pageView.totalPages())
                .build();
    }

    default AdminFolderItemResponse toAdminFolderItemResponse(AdminFolderItemView view) {
        return new AdminFolderItemResponse(
                view.id(),
                view.type() == null ? null : view.type().name().toLowerCase(),
                view.name(),
                view.tenantId(),
                view.tenantName(),
                view.parentId(),
                view.parentName(),
                view.sizeBytes(),
                view.mimeType(),
                view.status(),
                toUserSummaryResponse(view.actorId(), view.actorFullName()),
                view.createdAt(),
                view.updatedAt()
        );
    }

    private UserSummaryResponse toUserSummaryResponse(java.util.UUID actorId, String actorFullName) {
        if (actorId == null && actorFullName == null) {
            return null;
        }
        return new UserSummaryResponse(actorId, actorFullName);
    }

    default PageResponse<AdminFolderItemResponse> toAdminItemPageResponse(AdminFolderItemPageView pageView) {
        return PageResponse.<AdminFolderItemResponse>builder()
                .data(pageView.data().stream().map(this::toAdminFolderItemResponse).toList())
                .currentPage(pageView.page())
                .pageSize(pageView.limit())
                .totalElements(pageView.total())
                .totalPages(pageView.totalPages())
                .build();
    }
}
