package com.filestorage.application.usecase.file;

import com.filestorage.adapter.out.persistence.entity.MultipartUploadSession;
import com.filestorage.adapter.out.persistence.repository.MultipartUploadSessionJpaRepository;
import com.filestorage.domain.model.enums.MultipartUploadStatus;
import com.filestorage.domain.model.file.PartPresignedUrlView;
import com.filestorage.domain.port.in.file.GetPartPresignedUrlUseCase;
import com.filestorage.domain.port.out.CachePort;
import com.filestorage.domain.port.out.MultipartSessionCacheDto;
import com.filestorage.domain.port.out.ObjectStoragePort;
import com.filestorage.infrastructure.config.UploadProperties;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
@RequiredArgsConstructor
public class GetPartPresignedUrlService implements GetPartPresignedUrlUseCase {

    private final MultipartUploadSessionJpaRepository sessionRepository;
    private final ObjectStoragePort objectStoragePort;
    private final CachePort cachePort;
    private final UploadProperties uploadProperties;

    @Override
    @Transactional(readOnly = true)
    public PartPresignedUrlView execute(GetPartPresignedUrlCommand command) {
        // Cache-first: validate from Redis to avoid DB hit on the hot path
        var cached = cachePort.getMultipartSession(command.uploadId());
        if (cached.isPresent()) {
            return executeFromCache(cached.get(), command);
        }

        // Cache miss — fall back to DB
        MultipartUploadSession session = sessionRepository
                .findByUploadIdAndInitiatedById(command.uploadId(), command.userId())
                .orElseThrow(() -> new AppException(ErrorCode.UPLOAD_SESSION_NOT_FOUND));

        validateSession(session.getStatus(), session.getExpiresAt(),
                session.getTotalParts(), command.partNumber());

        return generateUrl(session.getUploadId(), session.getObjectKey(), command.partNumber());
    }

    private PartPresignedUrlView executeFromCache(MultipartSessionCacheDto cached,
                                                  GetPartPresignedUrlCommand command) {
        // Ownership check against cached initiatedByUserId
        if (!cached.initiatedByUserId().equals(command.userId())) {
            throw new AppException(ErrorCode.UPLOAD_SESSION_NOT_FOUND);
        }
        validateSession(cached.status(), cached.expiresAt(),
                cached.totalParts(), command.partNumber());

        return generateUrl(cached.uploadId(), cached.objectKey(), command.partNumber());
    }

    private void validateSession(MultipartUploadStatus status, Instant expiresAt,
                                 int totalParts, int partNumber) {
        if (status != MultipartUploadStatus.IN_PROGRESS) {
            throw new AppException(ErrorCode.INVALID_SESSION_STATUS);
        }
        if (Instant.now().isAfter(expiresAt)) {
            throw new AppException(ErrorCode.UPLOAD_SESSION_EXPIRED);
        }
        if (partNumber < 1 || partNumber > totalParts) {
            throw new AppException(ErrorCode.INVALID_PAYLOAD);
        }
    }

    private PartPresignedUrlView generateUrl(String uploadId, String objectKey, int partNumber) {
        int ttlMinutes = uploadProperties.getMultipart().getPartUrlTtlMinutes();
        String partUrl = objectStoragePort.generatePresignedPartUrl(uploadId, objectKey, partNumber, ttlMinutes);
        Instant expiresAt = Instant.now().plusSeconds((long) ttlMinutes * 60);
        return new PartPresignedUrlView(uploadId, partNumber, partUrl, expiresAt);
    }
}
