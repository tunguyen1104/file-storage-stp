package com.filestorage.domain.port.in.folder;

import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.folder.BreadcrumbView;

import java.util.List;
import java.util.UUID;

public interface GetFolderBreadcrumbsUseCase {

    List<BreadcrumbView> execute(GetFolderBreadcrumbsQuery query);

    record GetFolderBreadcrumbsQuery(
            UUID actorUserId,
            UUID actorTenantId,
            RoleCode actorRole,
            UUID folderId
    ) {
    }
}

