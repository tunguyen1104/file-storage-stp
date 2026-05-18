package com.filestorage.application.usecase.folder;

import com.filestorage.adapter.out.persistence.entity.Folder;
import com.filestorage.adapter.out.persistence.repository.FolderJpaRepository;
import com.filestorage.domain.model.folder.BreadcrumbView;
import com.filestorage.domain.port.in.folder.GetFolderBreadcrumbsUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GetFolderBreadcrumbsService implements GetFolderBreadcrumbsUseCase {

    private final FolderJpaRepository folderRepository;
    private final FolderSupport folderSupport;
    private final FolderAccessPolicy folderAccessPolicy;

    @Override
    @Transactional(readOnly = true)
    public List<BreadcrumbView> execute(GetFolderBreadcrumbsQuery query) {
        var actor = folderSupport.resolveActor(query.actorUserId(), query.actorTenantId());
        Folder folder = folderSupport.findReadableFolder(query.folderId(), actor.getTenant().getId(), query.actorRole());
        folderAccessPolicy.requireCanRead(folder, actor.getId(), actor.getTenant().getId(), query.actorRole());

        return folderRepository.findBreadcrumbs(folder.getId(), actor.getTenant().getId())
                .stream()
                .map(item -> new BreadcrumbView(item.getId(), item.getName()))
                .toList();
    }
}
