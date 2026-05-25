package com.filestorage.application.usecase.file;

import com.filestorage.adapter.out.persistence.entity.File;
import com.filestorage.adapter.out.persistence.mapper.FileViewMapper;
import com.filestorage.adapter.out.persistence.repository.FileJpaRepository;
import com.filestorage.adapter.out.persistence.repository.FileShareJpaRepository;
import com.filestorage.application.usecase.rag.RagLifecycleSyncService;
import com.filestorage.domain.model.audit.AuditAction;
import com.filestorage.domain.model.enums.FileStatus;
import com.filestorage.domain.model.file.FileView;
import com.filestorage.domain.port.in.file.DeleteFileUseCase;
import com.filestorage.infrastructure.audit.Auditable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
@Slf4j
@RequiredArgsConstructor
public class DeleteFileService implements DeleteFileUseCase {

    private final FileJpaRepository fileRepository;
    private final FileShareJpaRepository fileShareRepository;
    private final FileSupport fileSupport;
    private final FileViewMapper fileViewMapper;
    private final FileAccessPolicy fileAccessPolicy;
    private final RagLifecycleSyncService ragLifecycleSyncService;

    @Override
    @Transactional
    @Auditable(
            action = AuditAction.FILE_DELETE,
            fileIdSpel = "#command.fileId()",
            tenantIdSpel = "#result.tenantId()"
    )
    public FileView execute(DeleteFileCommand command) {
        var actor = fileSupport.resolveActor(command.actorUserId(), command.actorTenantId());
        File file = fileSupport.findReadableFile(command.fileId(), command.actorTenantId(), command.actorRole());
        fileAccessPolicy.requireCanModify(file, actor.getId(), actor.getTenant().getId(), command.actorRole());
        FileStatePolicy.requireActive(file);

        Instant now = Instant.now();
        file.setStatus(FileStatus.PENDING_DELETE);
        file.setDeletedAt(now);
        file.setScheduledDeleteAt(now.plus(Duration.ofDays(30)));
        fileShareRepository.revokeActiveSharesByFileId(file.getId(), now);
        File saved = fileRepository.save(file);
        try {
            ragLifecycleSyncService.onFileSoftDeleted(saved.getId());
        } catch (Exception e) {
            log.warn("DeleteFileService: failed to sync RAG soft delete fileId={}: {}", saved.getId(), e.getMessage());
        }
        return fileViewMapper.toFileView(saved);
    }

}
