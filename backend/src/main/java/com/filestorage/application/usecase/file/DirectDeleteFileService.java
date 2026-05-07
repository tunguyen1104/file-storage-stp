package com.filestorage.application.usecase.file;

import com.filestorage.adapter.out.persistence.entity.File;
import com.filestorage.adapter.out.persistence.entity.User;
import com.filestorage.adapter.out.persistence.mapper.FileViewMapper;
import com.filestorage.adapter.out.persistence.repository.FileJpaRepository;
import com.filestorage.adapter.out.persistence.repository.FileShareJpaRepository;
import com.filestorage.adapter.out.persistence.repository.UserJpaRepository;
import com.filestorage.domain.model.enums.FileStatus;
import com.filestorage.domain.model.file.FileView;
import com.filestorage.domain.port.in.file.DirectDeleteFileUseCase;
import com.filestorage.domain.port.out.ObjectStoragePort;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class DirectDeleteFileService implements DirectDeleteFileUseCase {

    private final FileJpaRepository fileRepository;
    private final FileShareJpaRepository fileShareRepository;
    private final UserJpaRepository userRepository;
    private final FileSupport fileSupport;
    private final ObjectStoragePort objectStoragePort;
    private final FileViewMapper fileViewMapper;

    @Override
    @Transactional
    public FileView execute(DirectDeleteFileCommand command) {
        if (command == null || command.fileId() == null) {
            throw new AppException(ErrorCode.INVALID_PAYLOAD);
        }

        fileSupport.resolveActor(command.actorUserId(), command.actorTenantId());
        File file = fileSupport.findReadableFile(command.fileId(), command.actorTenantId(), command.actorRole());

        if (shouldDecrementUsage(file)) {
            applyUsageDecrement(file);
        }

        fileShareRepository.deleteByFileId(file.getId());
        markDeleted(file);
        File saved = fileRepository.save(file);
        deleteStorageObject(file.getStorageKey());
        return fileViewMapper.toFileView(saved);
    }

    private boolean shouldDecrementUsage(File file) {
        return file.getStatus() == FileStatus.ACTIVE || file.getStatus() == FileStatus.PENDING_DELETE;
    }

    private void applyUsageDecrement(File file) {
        long size = file.getSizeBytes();
        User uploader = file.getUploadedBy();
        uploader.setStorageUsedBytes(Math.subtractExact(uploader.getStorageUsedBytes(), size));
        userRepository.save(uploader);
    }

    private void markDeleted(File file) {
        file.setStatus(FileStatus.DELETED);
        if (file.getDeletedAt() == null) {
            file.setDeletedAt(Instant.now());
        }
        file.setScheduledDeleteAt(null);
    }

    private void deleteStorageObject(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return;
        }
        objectStoragePort.delete(storageKey);
    }

}
