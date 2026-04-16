package com.filestorage.adapter.out.persistence.mapper;

import com.filestorage.adapter.out.persistence.entity.Tenant;
import com.filestorage.domain.model.tenant.TenantView;
import com.filestorage.infrastructure.config.GlobalMapperConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = GlobalMapperConfig.class)
public interface TenantViewMapper {

    @Mapping(target = "storageQuotaBytes", expression = "java(zeroIfNull(entity.getStorageQuotaBytes()))")
    @Mapping(target = "storageUsedBytes", expression = "java(zeroIfNull(entity.getStorageUsedBytes()))")
    @Mapping(target = "isActive", expression = "java(entity.isActive())")
    TenantView toTenantView(Tenant entity);

    default long zeroIfNull(Long value) {
        return value == null ? 0L : value;
    }
}

