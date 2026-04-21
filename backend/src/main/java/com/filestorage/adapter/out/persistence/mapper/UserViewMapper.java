package com.filestorage.adapter.out.persistence.mapper;

import com.filestorage.adapter.out.persistence.entity.Role;
import com.filestorage.adapter.out.persistence.entity.User;
import com.filestorage.domain.model.user.UserRoleView;
import com.filestorage.domain.model.user.UserStorageView;
import com.filestorage.domain.model.user.UserView;
import com.filestorage.infrastructure.config.GlobalMapperConfig;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(config = GlobalMapperConfig.class)
public interface UserViewMapper {

    @Mapping(target = "role", expression = "java(toRoleView(user.getRole()))")
    @Mapping(target = "tenantId", expression = "java(user.getTenant().getId())")
    @Mapping(target = "tenantName", expression = "java(user.getTenant().getName())")
    @Mapping(target = "isActive", expression = "java(user.isActive())")
    @Mapping(target = "storageQuotaBytes", expression = "java(zeroIfNull(user.getStorageQuotaBytes()))")
    @Mapping(target = "storageUsedBytes", expression = "java(zeroIfNull(user.getStorageUsedBytes()))")
    @Mapping(target = "createdBy", expression = "java(user.getCreatedBy() == null ? null : user.getCreatedBy().getId())")
    @Mapping(target = "createdByEmail", expression = "java(user.getCreatedBy() == null ? null : user.getCreatedBy().getEmail())")
    UserView toUserView(User user);

    @Mapping(target = "userId", expression = "java(user.getId())")
    @Mapping(target = "storageQuotaBytes", expression = "java(zeroIfNull(user.getStorageQuotaBytes()))")
    @Mapping(target = "storageUsedBytes", expression = "java(zeroIfNull(user.getStorageUsedBytes()))")
    @Mapping(target = "storageAvailableBytes", expression = "java(Math.max(0L, zeroIfNull(user.getStorageQuotaBytes()) - zeroIfNull(user.getStorageUsedBytes())))")
    @Mapping(target = "usagePercent", expression = "java(usagePercent(zeroIfNull(user.getStorageQuotaBytes()), zeroIfNull(user.getStorageUsedBytes())))")
    UserStorageView toUserStorageView(User user);

    default UserRoleView toRoleView(Role role) {
        return new UserRoleView(role.getId(), role.getCode(), role.getName());
    }

    default long zeroIfNull(Long value) {
        return value == null ? 0L : value;
    }

    default double usagePercent(long quota, long used) {
        if (quota <= 0) {
            return 0D;
        }
        return (used * 100D) / quota;
    }
}
