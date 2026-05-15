package com.filestorage.application.usecase.file;

import com.filestorage.adapter.out.persistence.entity.File;
import com.filestorage.adapter.out.persistence.entity.Folder;
import com.filestorage.adapter.out.persistence.entity.Tenant;
import com.filestorage.adapter.out.persistence.entity.User;
import com.filestorage.adapter.out.persistence.mapper.FileViewMapper;
import com.filestorage.adapter.out.persistence.repository.FileJpaRepository;
import com.filestorage.adapter.out.persistence.repository.FolderJpaRepository;
import com.filestorage.adapter.out.persistence.repository.UserJpaRepository;
import com.filestorage.application.usecase.folder.FolderAccessPolicy;
import com.filestorage.domain.model.enums.FileStatus;
import com.filestorage.domain.model.file.FileView;
import com.filestorage.domain.port.in.file.BulkUploadFilesUseCase;
import com.filestorage.domain.port.out.ObjectStoragePort;
import com.filestorage.infrastructure.config.StorageProperties;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BulkUploadFilesService implements BulkUploadFilesUseCase {

    private static final long BYTES_PER_MB = 1024L * 1024L;

    private final FileJpaRepository fileRepository;
    private final FolderJpaRepository folderRepository;
    private final UserJpaRepository userRepository;
    private final FileSupport fileSupport;
    private final FileViewMapper fileViewMapper;
    private final ObjectStoragePort objectStoragePort;
    private final StorageProperties storageProperties;
    private final FolderAccessPolicy folderAccessPolicy;

    @Override
    @Transactional
    public List<FileView> execute(BulkUploadCommand command) {
        validate(command);

        User actor = fileSupport.resolveActor(command.actorUserId(), command.actorTenantId());
        Tenant tenant = actor.getTenant();
        Folder folder = resolveFolder(command.folderId(), actor, command.actorRole());

        long totalBytes = totalBytes(command.items());
        enforceQuotas(actor, totalBytes);

        List<File> pendingFiles = new ArrayList<>();
        for (UploadItem item : command.items()) {
            String originalName = resolveOriginalName(item.originalName());
            String extension = extractExtension(originalName);
            UUID objectKeyId = UUID.randomUUID();
            String storageKey = objectStoragePort.buildStorageKey(
                    tenant.getId().toString(),
                    actor.getId().toString(),
                    objectKeyId.toString(),
                    extension
            );

            File pending = File.builder()
                    .folder(folder)
                    .tenant(tenant)
                    .uploadedBy(actor)
                    .originalName(originalName)
                    .storageKey(storageKey)
                    .mimeType(item.contentType())
                    .sizeBytes((long) item.content().length)
                    .status(FileStatus.PENDING)
                    .isIndexed(false)
                    .build();
            pendingFiles.add(fileRepository.save(pending));
        }

        List<FileView> results = new ArrayList<>();
        List<String> uploadedKeys = new ArrayList<>();
        try {
            for (int index = 0; index < pendingFiles.size(); index++) {
                File pending = pendingFiles.get(index);
                UploadItem item = command.items().get(index);
                objectStoragePort.upload(pending.getStorageKey(), item.content(), item.contentType());
                uploadedKeys.add(pending.getStorageKey());
            }

            incrementUsageOrFail(actor, totalBytes);

            for (File pending : pendingFiles) {
                pending.setStatus(FileStatus.ACTIVE);
                File saved = fileRepository.save(pending);
                results.add(fileViewMapper.toFileView(saved));
            }

            return results;
        } catch (RuntimeException ex) {
            cleanupUploadedObjects(uploadedKeys);
            throw ex instanceof AppException ? ex : new AppException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    private void validate(BulkUploadCommand command) {
        if (command == null || command.items() == null || command.items().isEmpty()) {
            throw new AppException(ErrorCode.INVALID_PAYLOAD);
        }

        long maxBytes = storageProperties.getSingleUploadMaxMb() * BYTES_PER_MB;
        for (UploadItem item : command.items()) {
            if (item == null || item.content() == null || item.content().length == 0) {
                throw new AppException(ErrorCode.INVALID_PAYLOAD);
            }
            if (item.originalName() == null || item.originalName().isBlank()) {
                throw new AppException(ErrorCode.INVALID_PAYLOAD);
            }
            if (item.content().length > maxBytes) {
                throw new AppException(ErrorCode.FILE_TOO_LARGE);
            }
        }
    }

    private Folder resolveFolder(UUID folderId, User actor, com.filestorage.domain.model.enums.RoleCode actorRole) {
        if (folderId == null) {
            return null;
        }
        Folder folder = folderRepository.findByIdAndTenantId(folderId, actor.getTenant().getId())
                .orElseThrow(() -> new AppException(ErrorCode.FOLDER_NOT_FOUND));
        folderAccessPolicy.requireCanWriteDirect(folder, actor.getId(), actor.getTenant().getId(), actorRole);
        return folder;
    }

    private void enforceQuotas(User actor, long bytes) {
        long newUserUsage = safeAdd(actor.getStorageUsedBytes(), bytes);
        if (newUserUsage > actor.getStorageQuotaBytes()) {
            throw new AppException(ErrorCode.USER_QUOTA_EXCEEDED);
        }
    }

    private long totalBytes(List<UploadItem> items) {
        long total = 0L;
        for (UploadItem item : items) {
            total = safeAdd(total, item.content().length);
        }
        return total;
    }

    private void incrementUsageOrFail(User actor, long bytes) {
        int updated = userRepository.incrementStorageUsedIfQuotaAllows(actor.getId(), bytes);
        if (updated == 0) {
            throw new AppException(ErrorCode.USER_QUOTA_EXCEEDED);
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

    private long safeAdd(long value, long delta) {
        return Math.addExact(value, delta);
    }

    private void cleanupUploadedObjects(List<String> uploadedKeys) {
        for (String storageKey : uploadedKeys) {
            if (storageKey == null || storageKey.isBlank()) {
                continue;
            }
            try {
                objectStoragePort.delete(storageKey);
            } catch (RuntimeException ignored) {
                // best-effort cleanup when bulk upload fails
            }
        }
    }
}
