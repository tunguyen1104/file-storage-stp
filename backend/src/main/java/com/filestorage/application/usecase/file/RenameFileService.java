package com.filestorage.application.usecase.file;

import com.filestorage.adapter.out.persistence.entity.File;
import com.filestorage.adapter.out.persistence.mapper.FileViewMapper;
import com.filestorage.adapter.out.persistence.repository.FileJpaRepository;
import com.filestorage.application.usecase.indexing.AfterCommitIndexingPublisher;
import com.filestorage.domain.event.FileIndexEvent;
import com.filestorage.domain.model.file.FileView;
import com.filestorage.domain.port.in.file.RenameFileUseCase;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class RenameFileService implements RenameFileUseCase {

    private final FileJpaRepository fileRepository;
    private final FileSupport fileSupport;
    private final FileViewMapper fileViewMapper;
    private final FileAccessPolicy fileAccessPolicy;
    private final AfterCommitIndexingPublisher indexingPublisher;

    @Override
    @Transactional
    public FileView execute(RenameFileCommand command) {
        var actor = fileSupport.resolveActor(command.actorUserId(), command.actorTenantId());
        File file = fileSupport.findReadableFile(command.fileId(), command.actorTenantId(), command.actorRole());
        fileAccessPolicy.requireCanModify(file, actor.getId(), actor.getTenant().getId(), command.actorRole());
        FileStatePolicy.requireActive(file);

        if (command.originalName() == null || command.originalName().isBlank()) {
            throw new AppException(ErrorCode.INVALID_PAYLOAD);
        }
        String newName = command.originalName().trim();

        fileRepository.updateOriginalName(file.getId(), newName);
        file.setOriginalName(newName);

        indexingPublisher.publishFileUpdated(new FileIndexEvent(
                file.getId(),
                file.getTenant().getId(),
                file.getUploadedBy().getId(),
                FileIndexEvent.Action.UPSERT_METADATA
        ));
        return fileViewMapper.toFileView(file);
    }


}
