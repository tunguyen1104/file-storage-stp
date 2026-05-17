package com.filestorage.adapter.in.rest.mapper;

import com.filestorage.adapter.in.rest.dto.response.ConfirmUploadResponse;
import com.filestorage.adapter.in.rest.dto.response.FileMetadataResponse;
import com.filestorage.adapter.in.rest.dto.response.FileResponse;
import com.filestorage.adapter.in.rest.dto.response.PageResponse;
import com.filestorage.adapter.in.rest.dto.response.PresignedUploadInitResponse;
import com.filestorage.adapter.in.rest.dto.response.UserSummaryResponse;
import com.filestorage.domain.model.file.ConfirmUploadView;
import com.filestorage.domain.model.file.FilePageView;
import com.filestorage.domain.model.file.FileView;
import com.filestorage.domain.model.file.PresignedUploadView;
import com.filestorage.infrastructure.config.GlobalMapperConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = GlobalMapperConfig.class)
public interface FileRestMapper {

    @Mapping(target = "uploadedBy", expression = "java(toUserSummaryResponse(view))")
    FileResponse toFileResponse(FileView view);

    @Mapping(target = "uploadedBy", expression = "java(toUserSummaryResponse(view))")
    FileMetadataResponse toFileMetadataResponse(FileView view);

    default UserSummaryResponse toUserSummaryResponse(FileView view) {
        if (view == null || view.uploadedById() == null) {
            return null;
        }
        return new UserSummaryResponse(view.uploadedById(), view.uploadedByFullName());
    }

    default PageResponse<FileResponse> toPageResponse(FilePageView pageView) {
        return PageResponse.<FileResponse>builder()
                .data(pageView.data().stream().map(this::toFileResponse).toList())
                .currentPage(pageView.page())
                .pageSize(pageView.limit())
                .totalElements(pageView.total())
                .totalPages(pageView.totalPages())
                .build();
    }

    default PageResponse<FileMetadataResponse> toMetadataPageResponse(FilePageView pageView) {
        return PageResponse.<FileMetadataResponse>builder()
                .data(pageView.data().stream().map(this::toFileMetadataResponse).toList())
                .currentPage(pageView.page())
                .pageSize(pageView.limit())
                .totalElements(pageView.total())
                .totalPages(pageView.totalPages())
                .build();
    }

    PresignedUploadInitResponse toPresignedUploadInitResponse(PresignedUploadView view);

    @Mapping(target = "message", constant = "File is being scanned for viruses. Please check back shortly.")
    ConfirmUploadResponse toConfirmUploadResponse(ConfirmUploadView view);
}

