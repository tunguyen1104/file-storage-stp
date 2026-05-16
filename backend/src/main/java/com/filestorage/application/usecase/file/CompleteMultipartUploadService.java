package com.filestorage.application.usecase.file;

import com.filestorage.adapter.out.persistence.entity.MultipartUploadSession;
import com.filestorage.adapter.out.persistence.repository.MultipartUploadSessionJpaRepository;
import com.filestorage.domain.model.enums.FileStatus;
import com.filestorage.domain.model.enums.MultipartUploadStatus;
import com.filestorage.domain.model.file.MultipartCompleteView;
import com.filestorage.domain.port.in.file.CompleteMultipartUploadUseCase;
import com.filestorage.domain.port.out.CachePort;
import com.filestorage.domain.port.out.ObjectStoragePort;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class CompleteMultipartUploadService implements CompleteMultipartUploadUseCase {

    private final MultipartUploadSessionJpaRepository sessionRepository;
    private final ObjectStoragePort objectStoragePort;
    private final CachePort cachePort;
    private final UploadStorageVerifier uploadStorageVerifier;

    @Override
    @Transactional
    public MultipartCompleteView execute(CompleteMultipartUploadCommand command) {
        if (command.parts() == null || command.parts().isEmpty()) {
            throw new AppException(ErrorCode.INVALID_PAYLOAD);
        }

        MultipartUploadSession session = sessionRepository
                .findByUploadIdAndInitiatedByIdForUpdate(command.uploadId(), command.userId())
                .orElseThrow(() -> new AppException(ErrorCode.UPLOAD_SESSION_NOT_FOUND));

        if (session.getStatus() != MultipartUploadStatus.IN_PROGRESS) {
            throw new AppException(ErrorCode.INVALID_SESSION_STATUS);
        }

        if (command.parts().size() != session.getTotalParts()) {
            throw new AppException(ErrorCode.PARTS_INCOMPLETE);
        }

        // Convert to domain-safe descriptors for the storage port, sorted by partNumber
        // S3/MinIO requires parts in ascending order — parallel uploads may arrive out of order
        List<ObjectStoragePort.PartDescriptor> partDescriptors = command.parts().stream()
                .sorted(java.util.Comparator.comparingInt(CompleteMultipartUploadUseCase.PartInfo::partNumber))
                .map(p -> new ObjectStoragePort.PartDescriptor(p.partNumber(), p.etag()))
                .toList();

        // Instruct MinIO to assemble parts into the final object (no bytes through backend)
        objectStoragePort.completeMultipartUpload(
                session.getUploadId(),
                session.getObjectKey(),
                partDescriptors
        );

        var file = session.getFile();
        String storageKey = session.getObjectKey();

        // Verify actual assembled size matches the declared total size (quota fraud prevention)
        uploadStorageVerifier.verifySizeOrFail(file, storageKey);

        // Re-detect real MIME type via Tika to prevent spoofing after assembly
        uploadStorageVerifier.detectAndCorrectMime(file, storageKey);

        // Transition session to terminal state and evict Redis cache
        session.setStatus(MultipartUploadStatus.COMPLETED);
        sessionRepository.save(session);
        cachePort.evictMultipartSession(session.getUploadId());

        // Route to ClamAV scan or activate directly if eligible for bypass
        FileStatus resultStatus = uploadStorageVerifier.routeToScanOrActivate(
                file, command.tenantId(), command.userId());

        return new MultipartCompleteView(file.getId(), resultStatus);
    }
}
