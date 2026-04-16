package com.filestorage.adapter.in.rest.mapper;

import com.filestorage.adapter.in.rest.dto.response.RoleSummaryResponse;
import com.filestorage.adapter.in.rest.dto.response.UserCreatedResponse;
import com.filestorage.adapter.in.rest.dto.response.UserDetailResponse;
import com.filestorage.adapter.in.rest.dto.response.UserListItemResponse;
import com.filestorage.adapter.in.rest.dto.response.UserListResponse;
import com.filestorage.adapter.in.rest.dto.response.UserPaginationResponse;
import com.filestorage.adapter.in.rest.dto.response.UserStorageResponse;
import com.filestorage.domain.model.user.UserPageView;
import com.filestorage.domain.model.user.UserRoleView;
import com.filestorage.domain.model.user.UserStorageView;
import com.filestorage.domain.model.user.UserView;
import com.filestorage.infrastructure.config.GlobalMapperConfig;
import org.mapstruct.Mapper;

@Mapper(config = GlobalMapperConfig.class)
public interface UserRestMapper {

    default UserCreatedResponse toUserCreatedResponse(UserView view) {
        if (view == null) {
            return null;
        }
        return new UserCreatedResponse(
                view.id(),
                view.email(),
                view.fullName(),
                toRoleSummaryResponse(view.role()),
                view.tenantId(),
                view.isActive(),
                view.storageQuotaBytes(),
                view.storageUsedBytes(),
                view.createdBy(),
                view.createdAt()
        );
    }

    UserDetailResponse toUserDetailResponse(UserView view);

    UserListItemResponse toUserListItemResponse(UserView view);

    UserStorageResponse toUserStorageResponse(UserStorageView view);

    RoleSummaryResponse toRoleSummaryResponse(UserRoleView view);

    default UserListResponse toUserListResponse(UserPageView pageView) {
        return new UserListResponse(
                pageView.data().stream().map(this::toUserListItemResponse).toList(),
                new UserPaginationResponse(pageView.page(), pageView.limit(), pageView.total(), pageView.totalPages())
        );
    }
}
