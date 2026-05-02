package com.filestorage.domain.port.in.file;

import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.file.ConfirmUploadView;

import java.util.UUID;

public interface ConfirmUploadUseCase {

    ConfirmUploadView execute(ConfirmUploadCommand command);

    record ConfirmUploadCommand(
            UUID userId,
            UUID tenantId,
            RoleCode role,
            UUID fileId
    ) {
    }
}
