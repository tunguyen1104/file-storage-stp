package com.filestorage.application.usecase.file;

import com.filestorage.adapter.out.persistence.entity.File;
import com.filestorage.adapter.out.persistence.mapper.FileViewMapper;
import com.filestorage.domain.model.file.FileView;
import com.filestorage.domain.port.in.file.GetFileMetadataUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetFileMetadataService implements GetFileMetadataUseCase {

    private final FileSupport fileSupport;
    private final FileViewMapper fileViewMapper;
    private final FileAccessPolicy fileAccessPolicy;

    @Override
    @Transactional(readOnly = true)
    public FileView execute(GetFileMetadataQuery query) {
        var actor = fileSupport.resolveActor(query.actorUserId(), query.actorTenantId());
        File file = fileSupport.findReadableFile(query.fileId(), query.actorTenantId(), query.actorRole());
        fileAccessPolicy.requireCanDownload(file, actor.getId(), actor.getTenant().getId(), query.actorRole());

        return fileViewMapper.toFileView(file);
    }
}
