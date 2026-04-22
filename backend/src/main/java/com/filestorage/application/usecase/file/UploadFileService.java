package com.filestorage.application.usecase.file;

import com.filestorage.adapter.out.persistence.entity.File;
import com.filestorage.adapter.out.persistence.entity.Folder;
import com.filestorage.adapter.out.persistence.entity.Tenant;
import com.filestorage.adapter.out.persistence.entity.User;
import com.filestorage.adapter.out.persistence.mapper.FileViewMapper;
import com.filestorage.adapter.out.persistence.repository.FileJpaRepository;
import com.filestorage.adapter.out.persistence.repository.FolderJpaRepository;
import com.filestorage.adapter.out.persistence.repository.TenantJpaRepository;
import com.filestorage.adapter.out.persistence.repository.UserJpaRepository;
import com.filestorage.domain.model.enums.FileStatus;
import com.filestorage.domain.model.file.FileView;
import com.filestorage.domain.port.in.file.UploadFileUseCase;
import com.filestorage.domain.port.out.ObjectStoragePort;
import com.filestorage.infrastructure.config.StorageProperties;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UploadFileService implements UploadFileUseCase {

    private static final long BYTES_PER_MB = 1024L * 1024L;

    private final FileJpaRepository fileRepository;
    private final FolderJpaRepository folderRepository;
    private final TenantJpaRepository tenantRepository;
    private final UserJpaRepository userRepository;
    private final FileSupport fileSupport;
    private final FileViewMapper fileViewMapper;
    private final ObjectStoragePort objectStoragePort;
    private final StorageProperties storageProperties;

    @Override
    public FileView execute(UploadFileCommand command) {
        validate(command);

        User actor = fileSupport.resolveActor(command.actorUserId(), command.actorTenantId());

        Tenant tenant = actor.getTenant();
        Folder folder = resolveFolder(command.folderId(), tenant.getId());
        String originalName = resolveOriginalName(command.originalName());
        String extension = extractExtension(originalName);
        // Keep storage key deterministic without forcing the JPA entity id path.
        UUID objectKeyId = UUID.randomUUID();
        String storageKey = objectStoragePort.buildStorageKey(tenant.getId().toString(), actor.getId().toString(), objectKeyId.toString(), extension);

        enforceQuotas(actor, tenant, command.content().length);

        File pending = File.builder()
                .folder(folder)
                .tenant(tenant)
                .uploadedBy(actor)
                .originalName(originalName)
                .storageKey(storageKey)
                .mimeType(command.contentType())
                .sizeBytes((long) command.content().length)
                .status(FileStatus.PENDING)
                .isIndexed(false)
                .build();
        pending = fileRepository.save(pending);

        try {
            objectStoragePort.upload(storageKey, command.content(), command.contentType());
            pending.setStatus(FileStatus.ACTIVE);
            File saved = fileRepository.save(pending);
            tenant.setStorageUsedBytes(safeAdd(tenant.getStorageUsedBytes(), command.content().length));
            actor.setStorageUsedBytes(safeAdd(actor.getStorageUsedBytes(), command.content().length));
            tenantRepository.save(tenant);
            userRepository.save(actor);
            return fileViewMapper.toFileView(saved);
        } catch (RuntimeException ex) {
            pending.setStatus(FileStatus.UPLOAD_FAILED);
            fileRepository.save(pending);
            throw ex instanceof AppException ? ex : new AppException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private void validate(UploadFileCommand command) {
        if (command == null || command.content() == null || command.content().length == 0) {
            throw new AppException(ErrorCode.INVALID_PAYLOAD);
        }
        long maxBytes = storageProperties.getSingleUploadMaxMb() * BYTES_PER_MB;
        if (command.content().length > maxBytes) {
            throw new AppException(ErrorCode.FILE_TOO_LARGE);
        }
        if (command.originalName() == null || command.originalName().isBlank()) {
            throw new AppException(ErrorCode.INVALID_PAYLOAD);
        }
    }

    private Folder resolveFolder(UUID folderId, UUID tenantId) {
        if (folderId == null) {
            return null;
        }
        return folderRepository.findByIdAndTenantId(folderId, tenantId)
                .orElseThrow(() -> new AppException(ErrorCode.FOLDER_NOT_FOUND));
    }

    private void enforceQuotas(User actor, Tenant tenant, int bytes) {
        long newUserUsage = safeAdd(actor.getStorageUsedBytes(), bytes);
        long newTenantUsage = safeAdd(tenant.getStorageUsedBytes(), bytes);
        if (newUserUsage > actor.getStorageQuotaBytes()) {
            throw new AppException(ErrorCode.USER_QUOTA_EXCEEDED);
        }
        if (newTenantUsage > tenant.getStorageQuotaBytes()) {
            throw new AppException(ErrorCode.TENANT_QUOTA_EXCEEDED);
        }
    }

    private String resolveOriginalName(String originalName) {
        return originalName.trim();
    }

    private String extractExtension(String name) {
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1);
    }

    private long safeAdd(long value, int delta) {
        return Math.addExact(value, delta);
    }
}




