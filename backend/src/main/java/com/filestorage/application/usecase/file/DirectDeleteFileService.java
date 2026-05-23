package com.filestorage.application.usecase.file;

import com.filestorage.adapter.out.persistence.entity.File;
import com.filestorage.adapter.out.persistence.entity.User;
import com.filestorage.adapter.out.persistence.mapper.FileViewMapper;
import com.filestorage.domain.model.audit.AuditAction;
import com.filestorage.domain.model.file.FileView;
import com.filestorage.domain.port.in.file.DirectDeleteFileUseCase;
import com.filestorage.infrastructure.audit.Auditable;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DirectDeleteFileService implements DirectDeleteFileUseCase {

    private final FileSupport fileSupport;
    private final FileHardDeleteSupport hardDeleteSupport;
    private final FileAccessPolicy fileAccessPolicy;
    private final FileViewMapper fileViewMapper;

    @Override
    @Transactional
    @Auditable(
            action = AuditAction.FILE_PURGED,
            fileIdSpel = "#command.fileId()",
            tenantIdSpel = "#result.tenantId()",
            extraSpel = "{'reason': 'hard_delete'}"
    )
    public FileView execute(DirectDeleteFileCommand command) {
        if (command == null || command.fileId() == null) {
            throw new AppException(ErrorCode.INVALID_PAYLOAD);
        }

        User actor = fileSupport.resolveActor(command.actorUserId(), command.actorTenantId());
        File file = fileSupport.findReadableFile(command.fileId(), command.actorTenantId(), command.actorRole());
        fileAccessPolicy.requireCanModify(file, actor.getId(), actor.getTenant().getId(), command.actorRole());
        hardDeleteSupport.hardDelete(file);
        return fileViewMapper.toFileView(file);
    }

}
