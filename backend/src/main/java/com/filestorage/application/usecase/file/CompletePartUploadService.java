package com.filestorage.application.usecase.file;

import com.filestorage.adapter.out.persistence.entity.MultipartUploadSession;
import com.filestorage.adapter.out.persistence.repository.MultipartUploadSessionJpaRepository;
import com.filestorage.domain.model.enums.MultipartUploadStatus;
import com.filestorage.domain.model.file.PartCompletionView;
import com.filestorage.domain.port.in.file.CompletePartUploadUseCase;
import com.filestorage.domain.port.out.CachePort;
import com.filestorage.domain.port.out.MultipartSessionCacheDto;
import com.filestorage.infrastructure.config.UploadProperties;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CompletePartUploadService implements CompletePartUploadUseCase {

    private final MultipartUploadSessionJpaRepository sessionRepository;
    private final CachePort cachePort;
    private final UploadProperties uploadProperties;

    @Override
    @Transactional
    public PartCompletionView execute(CompletePartUploadCommand command) {
        if (command.etag() == null || command.etag().isBlank()) {
            throw new AppException(ErrorCode.INVALID_PAYLOAD);
        }

        MultipartUploadSession session = sessionRepository
                .findByUploadIdAndInitiatedById(command.uploadId(), command.userId())
                .orElseThrow(() -> new AppException(ErrorCode.UPLOAD_SESSION_NOT_FOUND));

        if (session.getStatus() != MultipartUploadStatus.IN_PROGRESS) {
            throw new AppException(ErrorCode.INVALID_SESSION_STATUS);
        }

        // Append the new part — build a fresh mutable list to avoid immutable-list mutation
        List<MultipartUploadSession.PartInfo> updated = new ArrayList<>(session.getUploadedParts());
        updated.add(new MultipartUploadSession.PartInfo(command.partNumber(), command.etag()));
        session.setUploadedParts(updated);
        sessionRepository.save(session);

        // Update Redis cache
        int sessionTtlHours = uploadProperties.getMultipart().getSessionTtlHours();
        MultipartSessionCacheDto dto = new MultipartSessionCacheDto(
                session.getUploadId(),
                session.getFile().getId(),
                session.getInitiatedBy().getId(),
                session.getObjectKey(),
                session.getTotalParts(),
                session.getTotalSizeBytes(),
                session.getStatus(),
                session.getExpiresAt(),
                updated.stream()
                        .map(p -> new MultipartSessionCacheDto.PartInfo(p.partNumber(), p.etag()))
                        .toList()
        );
        cachePort.cacheMultipartSession(session.getUploadId(), dto, Duration.ofHours(sessionTtlHours));

        return new PartCompletionView(
                session.getUploadId(),
                command.partNumber(),
                updated.size(),
                session.getTotalParts()
        );
    }
}
