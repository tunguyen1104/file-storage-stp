package com.filestorage.application.usecase.file;

import com.filestorage.adapter.out.persistence.entity.File;
import com.filestorage.adapter.out.persistence.repository.FileJpaRepository;
import com.filestorage.domain.model.enums.FileStatus;
import com.filestorage.domain.port.out.ObjectStoragePort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Commits an {@link FileStatus#UPLOAD_FAILED} status update and purges the
 * associated MinIO object in a dedicated transaction that is independent of the
 * caller's outer transaction.
 *
 * <p>Why a separate bean?<br>
 * Spring {@code @Transactional} is implemented via AOP proxy — self-invocations
 * (i.e. {@code this.method()}) bypass the proxy and never create a new transaction.
 * Extracting this logic into its own Spring-managed bean ensures that
 * {@link Propagation#REQUIRES_NEW} takes effect: the DB update is committed
 * <em>before</em> the {@code AppException} propagates to and rolls back the
 * caller's transaction, so the file is always marked {@code UPLOAD_FAILED}
 * even when the outer transaction is rolled back.
 */
@Component
@RequiredArgsConstructor
public class FailedUploadPurger {

    private final FileJpaRepository fileRepository;
    private final ObjectStoragePort objectStoragePort;

    /**
     * Marks {@code file} as {@link FileStatus#UPLOAD_FAILED}, flushes the change
     * in a new independent transaction, then deletes the object from MinIO.
     *
     * @param file       the file entity to mark as failed
     * @param storageKey the MinIO object key to purge
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markFailedAndPurge(File file, String storageKey) {
        file.setStatus(FileStatus.UPLOAD_FAILED);
        fileRepository.save(file);
        // MinIO delete is intentionally after the DB commit — the status must be
        // persisted first so there is no window where the object exists but the
        // row still shows PENDING.
        objectStoragePort.deleteObjectIfExists(storageKey);
    }
}
