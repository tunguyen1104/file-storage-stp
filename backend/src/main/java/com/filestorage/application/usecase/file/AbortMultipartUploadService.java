package com.filestorage.application.usecase.file;

import com.filestorage.adapter.out.persistence.entity.MultipartUploadSession;
import com.filestorage.adapter.out.persistence.repository.FileJpaRepository;
import com.filestorage.adapter.out.persistence.repository.MultipartUploadSessionJpaRepository;
import com.filestorage.domain.model.enums.FileStatus;
import com.filestorage.domain.model.enums.MultipartUploadStatus;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.port.in.file.AbortMultipartUploadUseCase;
import com.filestorage.domain.port.out.CachePort;
import com.filestorage.domain.port.out.ObjectStoragePort;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class AbortMultipartUploadService implements AbortMultipartUploadUseCase {

    private final MultipartUploadSessionJpaRepository sessionRepository;
    private final FileJpaRepository fileRepository;
    private final ObjectStoragePort objectStoragePort;
    private final CachePort cachePort;

    @Override
    @Transactional
    public AbortView execute(AbortMultipartUploadCommand command) {
        // Tenant isolation enforced at query level
        MultipartUploadSession session = sessionRepository
                .findByUploadIdAndFileTenantId(command.uploadId(), command.tenantId())
                .orElseThrow(() -> new AppException(ErrorCode.UPLOAD_SESSION_NOT_FOUND));


        // Owner OR tenant admin may abort
        boolean isOwner = session.getInitiatedBy().getId().equals(command.userId());

        boolean isAdminOrAbove = command.role() != null
                && (command.role() == RoleCode.TENANT_ADMIN || command.role() == RoleCode.SUPER_ADMIN);

        if (!isOwner && !isAdminOrAbove) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        if (session.getStatus() != MultipartUploadStatus.IN_PROGRESS) {
            throw new AppException(ErrorCode.INVALID_SESSION_STATUS);
        }

        // Abort on MinIO — removes all intermediate parts
        try {
            objectStoragePort.abortMultipartUpload(session.getUploadId(), session.getObjectKey());
        } catch (Exception e) {
            log.warn("AbortMultipartUploadService: MinIO abort failed for uploadId={}: {}",
                    session.getUploadId(), e.getMessage());
        }

        // Transition statuses
        session.setStatus(MultipartUploadStatus.ABORTED);
        sessionRepository.save(session);

        com.filestorage.adapter.out.persistence.entity.File file = session.getFile();
        file.setStatus(FileStatus.UPLOAD_FAILED);
        fileRepository.save(file);

        // Evict Redis cache
        cachePort.evictMultipartSession(session.getUploadId());

        return new AbortView(session.getUploadId(), MultipartUploadStatus.ABORTED.name());
    }
}
