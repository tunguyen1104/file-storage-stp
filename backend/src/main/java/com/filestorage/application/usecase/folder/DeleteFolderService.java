package com.filestorage.application.usecase.folder;

import com.filestorage.adapter.out.persistence.entity.Folder;
import com.filestorage.adapter.out.persistence.entity.User;
import com.filestorage.adapter.out.persistence.repository.FileJpaRepository;
import com.filestorage.adapter.out.persistence.repository.FileShareJpaRepository;
import com.filestorage.adapter.out.persistence.repository.FolderJpaRepository;
import com.filestorage.application.usecase.indexing.AfterCommitIndexingPublisher;
import com.filestorage.domain.event.FolderIndexEvent;
import com.filestorage.domain.model.audit.AuditAction;
import com.filestorage.domain.model.enums.FileStatus;
import com.filestorage.domain.port.in.folder.DeleteFolderUseCase;
import com.filestorage.infrastructure.audit.Auditable;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeleteFolderService implements DeleteFolderUseCase {

    private static final List<FileStatus> FILE_STATUSES_ALLOWED_IN_DELETABLE_FOLDER = List.of(
            FileStatus.PENDING_DELETE,
            FileStatus.DELETED
    );

    private final FolderJpaRepository folderRepository;
    private final FileJpaRepository fileRepository;
    private final FileShareJpaRepository fileShareRepository;
    private final FolderSupport folderSupport;
    private final FolderAccessPolicy folderAccessPolicy;
    private final AfterCommitIndexingPublisher indexingPublisher;

    @Override
    @Transactional
    @Auditable(
            action = AuditAction.FOLDER_DELETE,
            extraSpel = "{'folder_id': #command.folderId() != null ? #command.folderId().toString() : null}"
    )
    public void execute(DeleteFolderCommand command) {
        User actor = folderSupport.resolveActor(command.actorUserId(), command.actorTenantId());
        Folder folder = folderSupport.findReadableFolder(command.folderId(), actor.getTenant().getId(), command.actorRole());
        folderAccessPolicy.requireCanModify(folder, actor.getId(), actor.getTenant().getId(), command.actorRole());

        UUID folderId = folder.getId();
        UUID tenantId = folder.getTenant().getId();
        if (folderRepository.existsByTenantIdAndParentId(tenantId, folderId)
                || fileRepository.existsByFolderIdAndStatusNotIn(folderId, FILE_STATUSES_ALLOWED_IN_DELETABLE_FOLDER)) {
            throw new AppException(ErrorCode.FOLDER_NOT_EMPTY);
        }

        fileRepository.clearFolderByFolderIdAndStatusIn(folderId, FILE_STATUSES_ALLOWED_IN_DELETABLE_FOLDER);
        fileShareRepository.deleteByFolderId(folderId);
        folderRepository.delete(folder);

        indexingPublisher.publishFolderDeleted(new FolderIndexEvent(
                folderId,
                tenantId,
                folder.getCreatedBy().getId(),
                FolderIndexEvent.Action.DELETE
        ));
    }
}
