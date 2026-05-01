package com.filestorage.application.usecase.folder;

import com.filestorage.adapter.out.persistence.entity.Folder;
import com.filestorage.adapter.out.persistence.entity.User;
import com.filestorage.adapter.out.persistence.repository.FileJpaRepository;
import com.filestorage.adapter.out.persistence.repository.FolderJpaRepository;
import com.filestorage.domain.model.enums.FileStatus;
import com.filestorage.domain.port.in.folder.DeleteFolderUseCase;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeleteFolderService implements DeleteFolderUseCase {

    private final FolderJpaRepository folderRepository;
    private final FileJpaRepository fileRepository;
    private final FolderSupport folderSupport;

    @Override
    @Transactional
    public void execute(DeleteFolderCommand command) {
        User actor = folderSupport.resolveActor(command.actorUserId(), command.actorTenantId());
        Folder folder = folderSupport.findReadableFolder(command.folderId(), actor.getTenant().getId(), command.actorRole());
        FolderAccessPolicy.requireCanModify(folder, actor.getId(), actor.getTenant().getId(), command.actorRole());

        UUID folderId = folder.getId();
        if (folderRepository.existsByTenantIdAndParentId(actor.getTenant().getId(), folderId)
                || fileRepository.existsByFolderIdAndStatus(folderId, FileStatus.ACTIVE)) {
            throw new AppException(ErrorCode.FOLDER_NOT_EMPTY);
        }

        folderRepository.delete(folder);
    }
}
