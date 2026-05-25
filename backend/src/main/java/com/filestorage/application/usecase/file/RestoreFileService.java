package com.filestorage.application.usecase.file;

import com.filestorage.adapter.out.persistence.entity.File;
import com.filestorage.adapter.out.persistence.mapper.FileViewMapper;
import com.filestorage.adapter.out.persistence.repository.FileJpaRepository;
import com.filestorage.adapter.out.persistence.repository.FolderJpaRepository;
import com.filestorage.application.usecase.rag.RagLifecycleSyncService;
import com.filestorage.domain.model.audit.AuditAction;
import com.filestorage.domain.model.enums.FileStatus;
import com.filestorage.domain.model.file.FileView;
import com.filestorage.domain.port.in.file.RestoreFileUseCase;
import com.filestorage.infrastructure.audit.Auditable;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class RestoreFileService implements RestoreFileUseCase {

    private final FileJpaRepository fileRepository;
    private final FolderJpaRepository folderRepository;
    private final FileSupport fileSupport;
    private final FileViewMapper fileViewMapper;
    private final FileAccessPolicy fileAccessPolicy;
    private final RagLifecycleSyncService ragLifecycleSyncService;

    @Override
    @Transactional
    @Auditable(
            action = AuditAction.FILE_RESTORE,
            fileIdSpel = "#command.fileId()",
            tenantIdSpel = "#result.tenantId()"
    )
    public FileView execute(RestoreFileCommand command) {
        var actor = fileSupport.resolveActor(command.actorUserId(), command.actorTenantId());
        File file = fileSupport.findReadableFile(command.fileId(), command.actorTenantId(), command.actorRole());
        fileAccessPolicy.requireCanModify(file, actor.getId(), actor.getTenant().getId(), command.actorRole());
        FileStatePolicy.requirePendingDelete(file);

        detachMissingFolder(file);
        file.setStatus(FileStatus.ACTIVE);
        file.setDeletedAt(null);
        file.setScheduledDeleteAt(null);
        File saved = fileRepository.save(file);
        try {
            ragLifecycleSyncService.onFileRestored(
                    saved.getId(),
                    saved.getTenant().getId(),
                    saved.getUploadedBy().getId(),
                    saved.getContentKey(),
                    saved.getOriginalName(),
                    saved.getFolder() == null ? null : saved.getFolder().getId()
            );
        } catch (Exception e) {
            log.warn("RestoreFileService: failed to sync RAG restore fileId={}: {}", saved.getId(), e.getMessage());
        }
        return fileViewMapper.toFileView(saved);
    }

    private void detachMissingFolder(File file) {
        if (file.getFolder() == null) {
            return;
        }
        boolean folderExists = folderRepository.existsByIdAndTenantId(file.getFolder().getId(), file.getTenant().getId());
        if (!folderExists) {
            file.setFolder(null);
        }
    }
}
