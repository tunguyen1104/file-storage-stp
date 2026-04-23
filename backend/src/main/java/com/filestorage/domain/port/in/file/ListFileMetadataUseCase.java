package com.filestorage.domain.port.in.file;

import com.filestorage.domain.model.enums.FileStatus;
import com.filestorage.domain.model.enums.FileSortBy;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.enums.SortDirection;
import com.filestorage.domain.model.file.FilePageView;

import java.util.UUID;

public interface ListFileMetadataUseCase {

    FilePageView execute(ListFileMetadataQuery query);

    record ListFileMetadataQuery(
            UUID actorUserId,
            UUID actorTenantId,
            RoleCode actorRole,
            UUID folderId,
            FileStatus status,
            UUID uploadedBy,
            UUID tenantId,
            String search,
            int page,
            int limit,
            FileSortBy sortBy,
            SortDirection sortDirection
    ) {
    }
}

