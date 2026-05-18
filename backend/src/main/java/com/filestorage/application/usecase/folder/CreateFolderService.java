package com.filestorage.application.usecase.folder;

import com.filestorage.adapter.out.persistence.entity.Folder;
import com.filestorage.adapter.out.persistence.entity.User;
import com.filestorage.adapter.out.persistence.repository.FolderJpaRepository;
import com.filestorage.domain.model.folder.FolderView;
import com.filestorage.domain.port.in.folder.CreateFolderUseCase;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreateFolderService implements CreateFolderUseCase {

    private static final int NAME_MAX_LENGTH = 255;

    private final FolderJpaRepository folderRepository;
    private final FolderSupport folderSupport;
    private final FolderAccessPolicy folderAccessPolicy;

    @Override
    @Transactional
    public FolderView execute(CreateFolderCommand command) {
        User actor = folderSupport.resolveActor(command.actorUserId(), command.actorTenantId());
        String name = normalizeName(command.name());

        Folder parent = null;
        if (command.parentId() != null) {
            parent = folderSupport.findReadableFolder(command.parentId(), actor.getTenant().getId(), command.actorRole());
            folderAccessPolicy.requireCanWriteDirect(parent, actor.getId(), actor.getTenant().getId(), command.actorRole());
        }

        UUID parentId = parent == null ? null : parent.getId();
        if (folderRepository.existsByTenantIdAndParentIdAndName(actor.getTenant().getId(), parentId, name)) {
            throw new AppException(ErrorCode.FOLDER_NAME_CONFLICT);
        }

        Folder folder = Folder.builder()
                .tenant(actor.getTenant())
                .parent(parent)
                .owner(actor)
                .name(name)
                .createdBy(actor)
                .build();

        Folder saved = folderRepository.save(folder);
        return FolderViewFactory.toFolderView(saved, 0L, null);
    }

    private String normalizeName(String name) {
        if (name == null || name.isBlank()) {
            throw new AppException(ErrorCode.INVALID_PAYLOAD);
        }
        String trimmed = name.trim();
        if (trimmed.length() > NAME_MAX_LENGTH) {
            throw new AppException(ErrorCode.INVALID_PAYLOAD);
        }
        return trimmed;
    }
}
