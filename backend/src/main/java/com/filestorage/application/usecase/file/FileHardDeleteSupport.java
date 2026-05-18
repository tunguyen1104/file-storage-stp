package com.filestorage.application.usecase.file;

import com.filestorage.adapter.out.persistence.entity.File;
import com.filestorage.adapter.out.persistence.entity.User;
import com.filestorage.adapter.out.persistence.repository.FileJpaRepository;
import com.filestorage.adapter.out.persistence.repository.FileShareJpaRepository;
import com.filestorage.adapter.out.persistence.repository.UserJpaRepository;
import com.filestorage.domain.model.enums.FileStatus;
import com.filestorage.domain.port.out.ObjectStoragePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Instant;

@Component
@RequiredArgsConstructor
public class FileHardDeleteSupport {

    private final FileJpaRepository fileRepository;
    private final FileShareJpaRepository fileShareRepository;
    private final UserJpaRepository userRepository;
    private final ObjectStoragePort objectStoragePort;

    public void hardDelete(File file) {
        if (shouldDecrementUsage(file)) {
            applyUsageDecrement(file);
        }
        fileShareRepository.deleteByFileId(file.getId());
        markDeleted(file);
        fileRepository.save(file);
        deleteStorageObject(file.getStorageKey());
    }

    private boolean shouldDecrementUsage(File file) {
        return file.getStatus() == FileStatus.ACTIVE || file.getStatus() == FileStatus.PENDING_DELETE;
    }

    private void applyUsageDecrement(File file) {
        User uploader = file.getUploadedBy();
        uploader.setStorageUsedBytes(Math.subtractExact(uploader.getStorageUsedBytes(), file.getSizeBytes()));
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
