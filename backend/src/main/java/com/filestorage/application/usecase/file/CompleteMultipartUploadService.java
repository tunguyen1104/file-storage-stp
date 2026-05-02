package com.filestorage.application.usecase.file;

import com.filestorage.adapter.out.persistence.entity.MultipartUploadSession;
import com.filestorage.adapter.out.persistence.repository.FileJpaRepository;
import com.filestorage.adapter.out.persistence.repository.MultipartUploadSessionJpaRepository;
import com.filestorage.domain.event.VirusScanRequestedEvent;
import com.filestorage.domain.model.enums.FileStatus;
import com.filestorage.domain.model.enums.MultipartUploadStatus;
import com.filestorage.domain.model.file.MultipartCompleteView;
import com.filestorage.domain.port.in.file.CompleteMultipartUploadUseCase;
import com.filestorage.domain.port.out.CachePort;
import com.filestorage.domain.port.out.ObjectStoragePort;
import com.filestorage.domain.port.out.VirusScanEventPublisherPort;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class CompleteMultipartUploadService implements CompleteMultipartUploadUseCase {

    private final MultipartUploadSessionJpaRepository sessionRepository;
    private final FileJpaRepository fileRepository;
    private final ObjectStoragePort objectStoragePort;
    private final VirusScanEventPublisherPort virusScanEventPublisherPort;
    private final CachePort cachePort;

    @Override
    @Transactional
    public MultipartCompleteView execute(CompleteMultipartUploadCommand command) {
        if (command.parts() == null || command.parts().isEmpty()) {
            throw new AppException(ErrorCode.INVALID_PAYLOAD);
        }

        MultipartUploadSession session = sessionRepository
                .findByUploadIdAndInitiatedById(command.uploadId(), command.userId())
                .orElseThrow(() -> new AppException(ErrorCode.UPLOAD_SESSION_NOT_FOUND));

        if (session.getStatus() != MultipartUploadStatus.IN_PROGRESS) {
            throw new AppException(ErrorCode.INVALID_SESSION_STATUS);
        }

        if (command.parts().size() != session.getTotalParts()) {
            throw new AppException(ErrorCode.PARTS_INCOMPLETE);
        }

        // Convert to domain-safe descriptors for the storage port
        List<ObjectStoragePort.PartDescriptor> partDescriptors = command.parts().stream()
                .map(p -> new ObjectStoragePort.PartDescriptor(p.partNumber(), p.etag()))
                .toList();

        // Instruct MinIO to assemble parts into the final object
        objectStoragePort.completeMultipartUpload(
                session.getUploadId(),
                session.getObjectKey(),
                partDescriptors
        );

        // Transition statuses
        session.setStatus(MultipartUploadStatus.COMPLETED);
        sessionRepository.save(session);

        com.filestorage.adapter.out.persistence.entity.File file = session.getFile();
        file.setStatus(FileStatus.SCANNING);
        fileRepository.save(file);

        // Evict Redis cache — session is terminal
        cachePort.evictMultipartSession(session.getUploadId());

        // Reuse existing virus-scan async pipeline
        virusScanEventPublisherPort.publishVirusScanRequested(new VirusScanRequestedEvent(
                file.getId(),
                file.getStorageKey(),
                command.tenantId(),
                command.userId()
        ));

        return new MultipartCompleteView(file.getId(), FileStatus.SCANNING);
    }
}
