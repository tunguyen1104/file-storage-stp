package com.filestorage.application.usecase.folder;

import com.filestorage.adapter.out.persistence.entity.Folder;
import com.filestorage.adapter.out.persistence.entity.User;
import com.filestorage.adapter.out.persistence.repository.FolderJpaRepository;
import com.filestorage.domain.model.folder.FolderView;
import com.filestorage.domain.port.in.folder.MoveFolderUseCase;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class MoveFolderService implements MoveFolderUseCase {

    private final FolderJpaRepository folderRepository;
    private final FolderSupport folderSupport;
    private final FolderAccessPolicy folderAccessPolicy;

    @Override
    @Transactional
    public FolderView execute(MoveFolderCommand command) {
        User actor = folderSupport.resolveActor(command.actorUserId(), command.actorTenantId());
        Folder folder = folderSupport.findReadableFolder(command.folderId(), actor.getTenant().getId(), command.actorRole());
        folderAccessPolicy.requireCanModify(folder, actor.getId(), actor.getTenant().getId(), command.actorRole());

        Folder newParent = null;
        if (command.parentId() != null) {
            newParent = folderSupport.findReadableFolder(command.parentId(), actor.getTenant().getId(), command.actorRole());
            folderAccessPolicy.requireCanWriteDirect(newParent, actor.getId(), actor.getTenant().getId(), command.actorRole());

            if (folderRepository.isDescendant(folder.getId(), actor.getTenant().getId(), newParent.getId())) {
                throw new AppException(ErrorCode.FOLDER_INVALID_MOVE);
            }
        }

        UUID newParentId = newParent == null ? null : newParent.getId();
        if (folderRepository.existsByTenantIdAndParentIdAndName(actor.getTenant().getId(), newParentId, folder.getName())) {
            throw new AppException(ErrorCode.FOLDER_NAME_CONFLICT);
        }

        folder.setParent(newParent);
        Folder saved = folderRepository.save(folder);
        return FolderViewFactory.toFolderView(saved, 0L, null);
    }
}
