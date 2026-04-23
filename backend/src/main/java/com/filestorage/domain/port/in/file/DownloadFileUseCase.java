package com.filestorage.domain.port.in.file;

import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.file.FileDownloadView;

import java.util.UUID;

public interface DownloadFileUseCase {

    FileDownloadView execute(DownloadFileQuery query);

    record DownloadFileQuery(
            UUID actorUserId,
            UUID actorTenantId,
            RoleCode actorRole,
            UUID fileId,
            Integer ttlMinutes
    ) {
    }
}

