package com.filestorage.application.usecase.file;

import com.filestorage.adapter.out.persistence.entity.File;
import com.filestorage.adapter.out.persistence.entity.Tenant;
import com.filestorage.adapter.out.persistence.entity.User;
import com.filestorage.adapter.out.persistence.repository.FileJpaRepository;
import com.filestorage.adapter.out.persistence.repository.FileShareJpaRepository;
import com.filestorage.adapter.out.persistence.repository.TenantJpaRepository;
import com.filestorage.adapter.out.persistence.repository.UserJpaRepository;
import com.filestorage.domain.model.enums.FileStatus;
import com.filestorage.domain.port.in.file.DirectDeleteFileUseCase;
import com.filestorage.domain.port.out.ObjectStoragePort;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DirectDeleteFileService implements DirectDeleteFileUseCase {

    private final FileJpaRepository fileRepository;
    private final FileShareJpaRepository fileShareRepository;
    private final TenantJpaRepository tenantRepository;
    private final UserJpaRepository userRepository;
    private final FileSupport fileSupport;
    private final ObjectStoragePort objectStoragePort;

    @Override
    @Transactional
    public void execute(DirectDeleteFileCommand command) {
        if (command == null || command.fileId() == null) {
            throw new AppException(ErrorCode.INVALID_PAYLOAD);
        }

        fileSupport.resolveActor(command.actorUserId(), command.actorTenantId());
        File file = fileSupport.findReadableFile(command.fileId(), command.actorTenantId(), command.actorRole());

        if (shouldDecrementUsage(file)) {
            applyUsageDecrement(file);
        }

        fileShareRepository.deleteByFileId(file.getId());
        deleteStorageObject(file.getStorageKey());
        fileRepository.delete(file);
    }

    private boolean shouldDecrementUsage(File file) {
        return file.getStatus() == FileStatus.ACTIVE || file.getStatus() == FileStatus.PENDING_DELETE;
    }

    private void applyUsageDecrement(File file) {
        long size = file.getSizeBytes() == null ? 0L : file.getSizeBytes();
        Tenant tenant = file.getTenant();
        User uploader = file.getUploadedBy();
        tenant.setStorageUsedBytes(safeSubtract(tenant.getStorageUsedBytes(), size));
        uploader.setStorageUsedBytes(safeSubtract(uploader.getStorageUsedBytes(), size));
        tenantRepository.save(tenant);
        userRepository.save(uploader);
    }

    private void deleteStorageObject(String storageKey) {
        if (storageKey == null || storageKey.isBlank()) {
            return;
        }
        objectStoragePort.delete(storageKey);
    }

    private long safeSubtract(long value, long delta) {
        long result = value - delta;
        return Math.max(result, 0L);
    }
}

