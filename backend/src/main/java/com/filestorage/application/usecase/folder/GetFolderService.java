package com.filestorage.application.usecase.folder;

import com.filestorage.adapter.out.persistence.entity.Folder;
import com.filestorage.adapter.out.persistence.repository.FolderJpaRepository;
import com.filestorage.domain.model.folder.BreadcrumbView;
import com.filestorage.domain.model.folder.FolderView;
import com.filestorage.domain.port.in.folder.GetFolderUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GetFolderService implements GetFolderUseCase {

    private final FolderJpaRepository folderRepository;
    private final FolderSupport folderSupport;

    @Override
    @Transactional(readOnly = true)
    public FolderView execute(GetFolderQuery query) {
        var actor = folderSupport.resolveActor(query.actorUserId(), query.actorTenantId());
        Folder folder = folderSupport.findReadableFolder(query.folderId(), actor.getTenant().getId(), query.actorRole());
        FolderAccessPolicy.requireCanRead(folder, actor.getId(), actor.getTenant().getId(), query.actorRole());

        List<BreadcrumbView> breadcrumbs = folderRepository.findBreadcrumbs(folder.getId(), actor.getTenant().getId())
                .stream()
                .map(item -> new BreadcrumbView(item.getId(), item.getName()))
                .toList();
        long sizeBytes = folderRepository.sumActiveFileSize(folder.getId(), actor.getTenant().getId());

        return FolderViewFactory.toFolderView(folder, sizeBytes, breadcrumbs);
    }
}

