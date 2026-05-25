package com.filestorage.domain.port.in.folder;

import com.filestorage.domain.model.enums.FolderItemType;
import com.filestorage.domain.model.enums.FolderSortBy;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.enums.SortDirection;
import com.filestorage.domain.model.folder.FolderItemPageView;

import java.util.UUID;

public interface ListFolderChildrenUseCase {

    FolderItemPageView execute(ListFolderChildrenQuery query);

    record ListFolderChildrenQuery(
            UUID actorUserId,
            UUID actorTenantId,
            RoleCode actorRole,
            UUID folderId,
            int page,
            int size,
            FolderSortBy sortBy,
            SortDirection sortDirection,
            FolderItemType type,
            String search,
            UUID createdBy
    ) {
    }
}
