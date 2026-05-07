package com.filestorage.domain.port.in.folder;

import com.filestorage.domain.model.enums.AdminFolderItemSortBy;
import com.filestorage.domain.model.enums.FileStatus;
import com.filestorage.domain.model.enums.FolderItemType;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.enums.SortDirection;
import com.filestorage.domain.model.folder.AdminFolderItemPageView;

import java.util.UUID;

public interface ListAllTenantFolderItemsUseCase {

    AdminFolderItemPageView execute(ListAllTenantFolderItemsQuery query);

    record ListAllTenantFolderItemsQuery(
            UUID actorUserId,
            UUID actorTenantId,
            RoleCode actorRole,
            UUID tenantId,
            FolderItemType type,
            UUID parentId,
            FileStatus status,
            UUID createdBy,
            String search,
            int page,
            int limit,
            AdminFolderItemSortBy sortBy,
            SortDirection sortDirection
    ) {
    }
}
