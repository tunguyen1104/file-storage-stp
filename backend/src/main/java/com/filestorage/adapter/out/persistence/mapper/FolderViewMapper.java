package com.filestorage.adapter.out.persistence.mapper;

import com.filestorage.adapter.out.persistence.entity.Folder;
import com.filestorage.infrastructure.config.GlobalMapperConfig;
import org.mapstruct.Mapper;

import java.util.UUID;

@Mapper(config = GlobalMapperConfig.class)
public interface FolderViewMapper {

    default UUID toFolderId(Folder folder) {
        return folder == null ? null : folder.getId();
    }
}

