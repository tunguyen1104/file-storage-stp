package com.filestorage.application.usecase.file;

import com.filestorage.adapter.out.persistence.entity.MultipartUploadSession;
import com.filestorage.adapter.out.persistence.repository.MultipartUploadSessionJpaRepository;
import com.filestorage.domain.model.file.MultipartSessionView;
import com.filestorage.domain.port.in.file.GetMultipartUploadSessionUseCase;
import com.filestorage.domain.port.out.CachePort;
import com.filestorage.domain.port.out.MultipartSessionCacheDto;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GetMultipartUploadSessionService implements GetMultipartUploadSessionUseCase {

    private final MultipartUploadSessionJpaRepository sessionRepository;
    private final CachePort cachePort;

    @Override
    @Transactional(readOnly = true)
    public MultipartSessionView execute(GetMultipartUploadSessionQuery query) {
        // Cache-first: supports resume-upload without hitting DB on each poll
        var cached = cachePort.getMultipartSession(query.uploadId());
        if (cached.isPresent()) {
            return fromCache(cached.get(), query.userId());
        }

        // Cache miss — fall back to DB
        MultipartUploadSession session = sessionRepository
                .findByUploadIdAndInitiatedById(query.uploadId(), query.userId())
                .orElseThrow(() -> new AppException(ErrorCode.UPLOAD_SESSION_NOT_FOUND));

        List<MultipartSessionView.PartInfo> parts = session.getUploadedParts().stream()
                .map(p -> new MultipartSessionView.PartInfo(p.partNumber(), p.etag()))
                .toList();

        return new MultipartSessionView(
                session.getUploadId(),
                session.getFile().getId(),
                session.getStatus(),
                session.getTotalParts(),
                parts,
                session.getExpiresAt()
        );
    }

    private MultipartSessionView fromCache(MultipartSessionCacheDto cached, java.util.UUID userId) {
        if (!cached.initiatedByUserId().equals(userId)) {
            throw new AppException(ErrorCode.UPLOAD_SESSION_NOT_FOUND);
        }

        List<MultipartSessionView.PartInfo> parts = cached.uploadedParts().stream()
                .map(p -> new MultipartSessionView.PartInfo(p.partNumber(), p.etag()))
                .toList();

        return new MultipartSessionView(
                cached.uploadId(),
                cached.fileId(),
                cached.status(),
                cached.totalParts(),
                parts,
                cached.expiresAt()
        );
    }
}
