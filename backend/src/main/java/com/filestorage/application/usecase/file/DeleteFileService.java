package com.filestorage.application.usecase.file;

import com.filestorage.adapter.out.persistence.entity.File;
import com.filestorage.adapter.out.persistence.mapper.FileViewMapper;
import com.filestorage.adapter.out.persistence.repository.FileJpaRepository;
import com.filestorage.adapter.out.persistence.repository.FileShareJpaRepository;
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
    private final FileShareJpaRepository fileShareRepository;
    private final FileSupport fileSupport;
    private final FileViewMapper fileViewMapper;
    private final FileAccessPolicy fileAccessPolicy;

    @Override
    @Transactional
    public FileView execute(DeleteFileCommand command) {
        var actor = fileSupport.resolveActor(command.actorUserId(), command.actorTenantId());
        File file = fileSupport.findReadableFile(command.fileId(), command.actorTenantId(), command.actorRole());
        fileAccessPolicy.requireCanModify(file, actor.getId(), actor.getTenant().getId(), command.actorRole());
        FileStatePolicy.requireActive(file);

        Instant now = Instant.now();
        file.setStatus(FileStatus.PENDING_DELETE);
        file.setDeletedAt(now);
        file.setScheduledDeleteAt(now.plus(Duration.ofDays(30)));
        fileShareRepository.revokeActiveSharesByFileId(file.getId(), now);
        return fileViewMapper.toFileView(fileRepository.save(file));
    }

}
