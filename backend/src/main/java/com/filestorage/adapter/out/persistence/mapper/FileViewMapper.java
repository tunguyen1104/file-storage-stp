package com.filestorage.adapter.out.persistence.mapper;

import com.filestorage.adapter.out.persistence.entity.File;
import com.filestorage.domain.model.file.FileView;
import com.filestorage.infrastructure.config.GlobalMapperConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = GlobalMapperConfig.class, uses = FolderViewMapper.class)
public interface FileViewMapper {

    @Mapping(target = "sizeBytes", expression = "java(zeroIfNull(file.getSizeBytes()))")
    @Mapping(target = "folderId", source = "folder")
    @Mapping(target = "tenantId", expression = "java(file.getTenant().getId())")
    @Mapping(target = "uploadedById", expression = "java(file.getUploadedBy().getId())")
    @Mapping(target = "uploadedByFullName", expression = "java(file.getUploadedBy().getFullName())")
    @Mapping(target = "isIndexed", expression = "java(file.isIndexed())")
    FileView toFileView(File file);

    default long zeroIfNull(Long value) {
        return value == null ? 0L : value;
    }
}

