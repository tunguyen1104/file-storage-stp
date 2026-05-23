package com.filestorage.application.usecase.file;

import com.filestorage.adapter.out.persistence.entity.File;
import com.filestorage.domain.model.audit.AuditAction;
import com.filestorage.domain.model.file.FilePresignedGetView;
import com.filestorage.domain.port.in.file.GetFilePresignedUrlUseCase;
import com.filestorage.domain.port.out.ObjectStoragePort;
import com.filestorage.infrastructure.audit.Auditable;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

@Service
@RequiredArgsConstructor
public class GetFilePresignedUrlService implements GetFilePresignedUrlUseCase {

    private final FileSupport fileSupport;
    private final ObjectStoragePort objectStoragePort;

    @Override
    @Transactional(readOnly = true)
    @Auditable(
            action = AuditAction.FILE_DOWNLOAD,
            fileIdSpel = "#query.fileId()",
            extraSpel = "{'ttl_minutes': #query.ttlMinutes()}"
    )
    public FilePresignedGetView execute(GetFilePresignedUrlQuery query) {
        FileSupport.DownloadContext context = fileSupport.resolveDownloadContext(
                query.actorUserId(),
                query.actorTenantId(),
                query.actorRole(),
                query.fileId(),
                query.ttlMinutes()
        );
        File file = context.file();
        int ttlMinutes = context.ttlMinutes();

        String url = objectStoragePort.generatePresignedGetUrl(
                file.getStorageKey(),
                file.getOriginalName(),
                Duration.ofMinutes(ttlMinutes)
        );
        Instant expiresAt = Instant.now().plus(Duration.ofMinutes(ttlMinutes));
        return new FilePresignedGetView(url, expiresAt, file.getOriginalName(), file.getMimeType(), file.getSizeBytes());
    }
}

