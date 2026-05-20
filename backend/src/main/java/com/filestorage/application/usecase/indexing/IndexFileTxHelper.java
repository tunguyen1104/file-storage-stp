package com.filestorage.application.usecase.indexing;

import com.filestorage.adapter.out.persistence.entity.File;
import com.filestorage.adapter.out.persistence.repository.FileJpaRepository;
import com.filestorage.domain.model.enums.FileStatus;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Holds the three transactional boundaries needed by {@link IndexFileService}.
 *
 * <p>Lives in its own Spring-managed bean so that AOP proxies are honoured.
 * If these methods stayed on {@code IndexFileService}, self-invocation
 * ({@code this.method()}) would bypass the proxy and the {@code @Transactional}
 * annotations would have no effect. Same pattern as
 * {@code FailedUploadPurger}.
 */
@Component
@RequiredArgsConstructor
public class IndexFileTxHelper {

    private final FileJpaRepository fileRepository;

    @Transactional(readOnly = true)
    public Optional<Snapshot> loadActiveSnapshot(UUID fileId) {
        File file = fileRepository.findByIdWithUploaderAndTenant(fileId)
                .orElse(null);
        if (file == null) {
            return Optional.empty();
        }

        return Optional.of(new Snapshot(
                file.getStatus(),
                file.getId(),
                file.getTenant().getId(),
                file.getFolder() == null ? null : file.getFolder().getId(),
                file.getUploadedBy().getId(),
                file.getOriginalName(),
                file.getMimeType(),
                file.getStorageKey(),
                file.getCreatedAt()
        ));
    }

    @Transactional
    public void markIndexed(UUID fileId, String contentKey) {
        fileRepository.markIndexed(fileId, contentKey);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void bumpRetryCount(UUID fileId) {
        fileRepository.incrementIndexRetryCount(fileId);
    }

    /**
     * Snapshot of a file taken inside a short, read-only transaction so the
     * connection is released before heavy I/O begins. {@link #status} is the
     * only field set when the file is not ACTIVE — callers should short-circuit
     * in that case.
     */
    public record Snapshot(
            FileStatus status,
            UUID id,
            UUID tenantId,
            UUID folderId,
            UUID uploadedBy,
            String originalName,
            String mimeType,
            String storageKey,
            Instant createdAt
    ) {}
}
