package com.filestorage.application.usecase.file;

import com.filestorage.adapter.out.persistence.entity.File;
import com.filestorage.adapter.out.persistence.entity.Folder;
import com.filestorage.adapter.out.persistence.entity.User;
import com.filestorage.adapter.out.persistence.mapper.FileViewMapper;
import com.filestorage.adapter.out.persistence.repository.FileJpaRepository;
import com.filestorage.adapter.out.persistence.repository.FolderJpaRepository;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.file.FileView;
import com.filestorage.domain.port.in.file.MoveFileUseCase;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class MoveFileService implements MoveFileUseCase {

    private final FileJpaRepository fileRepository;
    private final FolderJpaRepository folderRepository;
    private final FileSupport fileSupport;
    private final FileViewMapper fileViewMapper;

    @Override
    @Transactional
    public FileView execute(MoveFileCommand command) {
        User actor = fileSupport.resolveActor(command.actorUserId(), command.actorTenantId());
        File file = fileSupport.findReadableFile(command.fileId(), command.actorTenantId(), command.actorRole());
        FileAccessPolicy.requireCanModify(file, actor.getId(), actor.getTenant().getId(), command.actorRole());
        FileStatePolicy.requireActive(file);

        Folder targetFolder = null;
        if (command.folderId() != null) {
            targetFolder = folderRepository.findByIdAndTenantId(command.folderId(), file.getTenant().getId())
                    .orElseThrow(() -> new AppException(ErrorCode.FOLDER_NOT_FOUND));
            requireCanModifyFolder(targetFolder, actor, command.actorRole());
        }

        file.setFolder(targetFolder);
        return fileViewMapper.toFileView(fileRepository.save(file));
    }

    private void requireCanModifyFolder(Folder folder, User actor, RoleCode actorRole) {
        if (actorRole == RoleCode.SUPER_ADMIN || actorRole == RoleCode.TENANT_ADMIN) {
            return;
        }
        if (!folder.getTenant().getId().equals(actor.getTenant().getId())) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }
        if (!folder.getCreatedBy().getId().equals(actor.getId())) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }
    }
}

