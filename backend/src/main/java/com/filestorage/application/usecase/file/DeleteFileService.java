package com.filestorage.application.usecase.file;

import com.filestorage.adapter.out.persistence.entity.File;
import com.filestorage.adapter.out.persistence.mapper.FileViewMapper;
import com.filestorage.adapter.out.persistence.repository.FileJpaRepository;
import com.filestorage.domain.model.enums.FileStatus;
import com.filestorage.domain.model.file.FileView;
import com.filestorage.domain.port.in.file.DeleteFileUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class DeleteFileService implements DeleteFileUseCase {

    private final FileJpaRepository fileRepository;
    private final FileSupport fileSupport;
    private final FileViewMapper fileViewMapper;

    @Override
    @Transactional
    public FileView execute(DeleteFileCommand command) {
        var actor = fileSupport.resolveActor(command.actorUserId(), command.actorTenantId());
        File file = fileSupport.findReadableFile(command.fileId(), command.actorTenantId(), command.actorRole());
        FileAccessPolicy.requireCanModify(file, actor.getId(), actor.getTenant().getId(), command.actorRole());
        FileStatePolicy.requireActive(file);

        file.setStatus(FileStatus.PENDING_DELETE);
        file.setDeletedAt(Instant.now());
        file.setScheduledDeleteAt(Instant.now().plus(Duration.ofDays(30)));
        return fileViewMapper.toFileView(fileRepository.save(file));
    }

}

