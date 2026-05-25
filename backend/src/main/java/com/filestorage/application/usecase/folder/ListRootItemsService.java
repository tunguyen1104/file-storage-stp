package com.filestorage.application.usecase.folder;

import com.filestorage.domain.model.folder.FolderItemPageView;
import com.filestorage.domain.port.in.folder.ListRootItemsUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ListRootItemsService implements ListRootItemsUseCase {

    private final FolderSupport folderSupport;
    private final ListFolderChildrenService listFolderChildrenService;

    @Override
    @Transactional(readOnly = true)
    public FolderItemPageView execute(ListRootItemsQuery query) {
        var actor = folderSupport.resolveActor(query.actorUserId(), query.actorTenantId());
        folderSupport.validateCreatedByFilter(actor.getId(), actor.getTenant().getId(), query.actorRole(), query.createdBy());
        return listFolderChildrenService.listItems(
                actor.getId(),
                actor.getTenant().getId(),
                query.actorRole(),
                null,
                query.page(),
                query.size(),
                query.sortBy(),
                query.sortDirection(),
                query.type(),
                query.search(),
                query.createdBy(),
                true
        );
    }
}
