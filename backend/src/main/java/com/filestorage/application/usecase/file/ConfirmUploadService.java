package com.filestorage.application.usecase.file;

import com.filestorage.adapter.out.persistence.entity.File;
import com.filestorage.adapter.out.persistence.repository.FileJpaRepository;
import com.filestorage.domain.model.enums.FileStatus;
import com.filestorage.domain.model.file.ConfirmUploadView;
import com.filestorage.domain.port.in.file.ConfirmUploadUseCase;
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
public class ConfirmUploadService implements ConfirmUploadUseCase {

    private final FileJpaRepository fileRepository;
    private final ObjectStoragePort objectStoragePort;
    private final UploadStorageVerifier uploadStorageVerifier;

    @Override
    @Transactional
    public ConfirmUploadView execute(ConfirmUploadCommand command) {
        File file = fileRepository.findByIdAndTenantId(command.fileId(), command.tenantId())
                .orElseThrow(() -> new AppException(ErrorCode.FILE_NOT_FOUND));

        // Only the owner may confirm
        if (!file.getUploadedBy().getId().equals(command.userId())) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        // File must be in PENDING state
        if (file.getStatus() != FileStatus.PENDING) {
            throw new AppException(ErrorCode.INVALID_FILE_STATUS);
        }

        String storageKey = file.getStorageKey();

        // Verify the object actually exists in MinIO
        if (!objectStoragePort.objectExists(storageKey)) {
            file.setStatus(FileStatus.UPLOAD_FAILED);
            fileRepository.save(file);
            throw new AppException(ErrorCode.FILE_NOT_FOUND_ON_STORAGE);
        }

        // Verify declared size matches actual size in MinIO (quota fraud prevention)
        uploadStorageVerifier.verifySizeOrFail(file, storageKey);

        // Re-detect real MIME type via Tika to prevent spoofing (e.g. evil.exe → report.pdf)
        uploadStorageVerifier.detectAndCorrectMime(file, storageKey);

        // Route to ClamAV scan or activate directly if eligible for bypass
        FileStatus resultStatus = uploadStorageVerifier.routeToScanOrActivate(
                file, command.tenantId(), command.userId());

        return new ConfirmUploadView(file.getId(), resultStatus);
    }
}
