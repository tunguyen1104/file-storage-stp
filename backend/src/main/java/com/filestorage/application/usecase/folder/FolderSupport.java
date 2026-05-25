package com.filestorage.application.usecase.folder;

import com.filestorage.adapter.out.persistence.entity.Folder;
import com.filestorage.adapter.out.persistence.entity.User;
import com.filestorage.adapter.out.persistence.repository.FolderJpaRepository;
import com.filestorage.adapter.out.persistence.repository.UserJpaRepository;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.UUID;

@Component
@RequiredArgsConstructor
class FolderSupport {

    private final UserJpaRepository userRepository;
    private final FolderJpaRepository folderRepository;

    User resolveActor(UUID actorUserId, UUID actorTenantId) {
        User actor = userRepository.findByIdWithRoleAndTenant(actorUserId)
                .orElseThrow(() -> new AppException(ErrorCode.ACCESS_DENIED));
        if (actor.getTenant() == null || !actor.getTenant().getId().equals(actorTenantId)) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
        return actor;
    }

    Folder findReadableFolder(UUID folderId, UUID actorTenantId, RoleCode actorRole) {
        if (actorRole == RoleCode.SUPER_ADMIN) {
            return folderRepository.findById(folderId)
                    .orElseThrow(() -> new AppException(ErrorCode.FOLDER_NOT_FOUND));
        }
        return folderRepository.findByIdAndTenantId(folderId, actorTenantId)
                .orElseThrow(() -> new AppException(ErrorCode.FOLDER_NOT_FOUND));
    }

    void validateCreatedByFilter(UUID actorUserId, UUID actorTenantId, RoleCode actorRole, UUID createdBy) {
        if (createdBy == null || actorRole == RoleCode.SUPER_ADMIN) {
            return;
        }
        if (actorRole == RoleCode.MEMBER) {
            if (!createdBy.equals(actorUserId)) {
                throw new AppException(ErrorCode.FORBIDDEN);
            }
            return;
        }
        if (actorRole == RoleCode.TENANT_ADMIN
                && userRepository.countByIdsAndTenantId(List.of(createdBy), actorTenantId) != 1) {
            throw new AppException(ErrorCode.FORBIDDEN);
        }
    }
}
