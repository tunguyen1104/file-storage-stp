package com.filestorage.application.usecase.file;

import com.filestorage.adapter.out.persistence.entity.File;
import com.filestorage.adapter.out.persistence.entity.Folder;
import com.filestorage.adapter.out.persistence.entity.MultipartUploadSession;
import com.filestorage.adapter.out.persistence.entity.Tenant;
import com.filestorage.adapter.out.persistence.entity.User;
import com.filestorage.adapter.out.persistence.repository.FileJpaRepository;
import com.filestorage.adapter.out.persistence.repository.FolderJpaRepository;
import com.filestorage.adapter.out.persistence.repository.MultipartUploadSessionJpaRepository;
import com.filestorage.adapter.out.persistence.repository.UserJpaRepository;
import com.filestorage.application.usecase.folder.FolderAccessPolicy;
import com.filestorage.domain.model.enums.FileStatus;
import com.filestorage.domain.model.enums.MultipartUploadStatus;
import com.filestorage.domain.model.file.MultipartUploadInitView;
import com.filestorage.domain.port.in.file.InitMultipartUploadUseCase;
import com.filestorage.domain.port.out.CachePort;
import com.filestorage.domain.port.out.MultipartSessionCacheDto;
import com.filestorage.domain.port.out.ObjectStoragePort;
import com.filestorage.infrastructure.config.UploadProperties;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class InitMultipartUploadService implements InitMultipartUploadUseCase {

    private static final List<FileStatus> IN_FLIGHT_STATUSES = List.of(FileStatus.PENDING, FileStatus.SCANNING);

    private final FileJpaRepository fileRepository;
    private final FolderJpaRepository folderRepository;
    private final UserJpaRepository userRepository;
    private final MultipartUploadSessionJpaRepository sessionRepository;
    private final ObjectStoragePort objectStoragePort;
    private final CachePort cachePort;
    private final UploadProperties uploadProperties;
    private final FolderAccessPolicy folderAccessPolicy;

    @Override
    @Transactional
    public MultipartUploadInitView execute(InitMultipartUploadCommand command) {

        if (command.totalParts() < 1 || command.totalSizeBytes() <= 0
                || command.originalName() == null || command.originalName().isBlank()) {
            throw new AppException(ErrorCode.INVALID_PAYLOAD);
        }

        // Fast-exit: reject large file sizes before touching the DB or storage.
        if (command.totalSizeBytes() > uploadProperties.getMaxFileSizeBytes()) {
            throw new AppException(ErrorCode.FILE_TOO_LARGE);
        }

        // Pessimistic write lock to serialize concurrent quota checks
        User actor = userRepository.findByIdForUpdate(command.userId())
                .orElseThrow(() -> new AppException(ErrorCode.ACCESS_DENIED));

        if (actor.getTenant() == null || !actor.getTenant().getId().equals(command.tenantId())) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        Tenant tenant = actor.getTenant();

        long inFlightBytes = fileRepository.sumSizeBytesByUserAndStatuses(actor.getId(), IN_FLIGHT_STATUSES);
        long effectiveUsage = safeAdd(actor.getStorageUsedBytes(), inFlightBytes);
        long newUsage = safeAdd(effectiveUsage, command.totalSizeBytes());

        if (newUsage > actor.getStorageQuotaBytes()) {
            throw new AppException(ErrorCode.QUOTA_EXCEEDED);
        }

        Folder folder = getWritableFolder(command.folderId(), actor, command.role());

        String storageKey = objectStoragePort.buildStorageKey(
                tenant.getId().toString(),
                actor.getId().toString(),
                UUID.randomUUID().toString(),
                extractExtension(command.originalName().trim())
        );

        // Call MinIO to initiate the multipart upload — returns the MinIO uploadId
        String uploadId = objectStoragePort.initiateMultipartUpload(storageKey, command.mimeType());

        File pending = File.builder()
                .folder(folder)
                .tenant(tenant)
                .uploadedBy(actor)
                .originalName(command.originalName().trim())
                .storageKey(storageKey)
                .mimeType(command.mimeType())
                .sizeBytes(command.totalSizeBytes())
                .status(FileStatus.PENDING)
                .isIndexed(false)
                .build();
        pending = fileRepository.save(pending);

        int sessionTtlHours = uploadProperties.getMultipart().getSessionTtlHours();
        Instant expiresAt = Instant.now().plusSeconds((long) sessionTtlHours * 3600);

        MultipartUploadSession session = MultipartUploadSession.builder()
                .uploadId(uploadId)
                .file(pending)
                .initiatedBy(actor)
                .objectKey(storageKey)
                .originalName(command.originalName().trim())
                .mimeType(command.mimeType())
                .totalSizeBytes(command.totalSizeBytes())
                .totalParts(command.totalParts())
                .status(MultipartUploadStatus.IN_PROGRESS)
                .expiresAt(expiresAt)
                .build();
        sessionRepository.save(session);

        // Warm Redis cache
        MultipartSessionCacheDto cacheDto = toDto(session, pending.getId());
        cachePort.cacheMultipartSession(uploadId, cacheDto, Duration.ofHours(sessionTtlHours));

        return new MultipartUploadInitView(uploadId, pending.getId(), expiresAt);
    }

    private Folder getWritableFolder(UUID folderId, User actor, com.filestorage.domain.model.enums.RoleCode actorRole) {
        if (folderId == null) {
            return null;
        }
        Folder folder = folderRepository.findByIdAndTenantId(folderId, actor.getTenant().getId())
                .orElseThrow(() -> new AppException(ErrorCode.FOLDER_NOT_FOUND));
        folderAccessPolicy.requireCanWriteDirect(folder, actor.getId(), actor.getTenant().getId(), actorRole);
        return folder;
    }

    private static MultipartSessionCacheDto toDto(MultipartUploadSession session, UUID fileId) {
        return new MultipartSessionCacheDto(
                session.getUploadId(),
                fileId,
                session.getInitiatedBy().getId(),
                session.getObjectKey(),
                session.getTotalParts(),
                session.getTotalSizeBytes(),
                session.getStatus(),
                session.getExpiresAt(),
                List.of()
        );
    }

    private static String extractExtension(String name) {
        int dot = name.lastIndexOf('.');
        if (dot <= 0 || dot == name.length() - 1) {
            return "";
        }
        return name.substring(dot + 1);
    }

    private static long safeAdd(long value, long delta) {
        return Math.addExact(value, delta);
    }
}
