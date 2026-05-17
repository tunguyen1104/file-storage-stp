package com.filestorage.application.usecase.share;

import com.filestorage.adapter.out.persistence.entity.FileShare;
import com.filestorage.adapter.out.persistence.entity.Folder;
import com.filestorage.adapter.out.persistence.entity.User;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.share.ShareView;
import com.filestorage.domain.port.in.share.ShareFolderWithTenantUseCase;
import com.filestorage.domain.port.in.share.ShareWithTenantCommand;
import com.filestorage.domain.port.out.ShareRepositoryPort;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ShareFolderWithTenantService implements ShareFolderWithTenantUseCase {

    private final ShareRepositoryPort shareRepository;
    private final ShareServiceSupport support;

    @Override
    @Transactional
    public ShareView shareFolderWithTenant(ShareWithTenantCommand command) {
        User actor = support.resolveActor(command.actorId(), command.actorTenantId());
        UUID actorTenantId = support.tenantId(actor);

        ShareAccessPolicy.validateExpiresAt(command.expiresAt());
        ShareAccessPolicy.validateTenantAdminTargetTenant(command.actorRole(), actorTenantId, command.targetTenantId());

        Folder folder = support.findShareableFolder(command.resourceId(), actorTenantId);
        ShareAccessPolicy.validateOwnership(support.folderOwnerId(folder), actor.getId());

        FileShare share = support.newShare(actorTenantId, actor.getId(), command.expiresAt());
        share.setFolderId(folder.getId());

        if (command.actorRole() == RoleCode.SUPER_ADMIN && command.targetTenantId() == null) {
            if (shareRepository.existsActiveAllTenantsFolderShare(folder.getId())) {
                throw new AppException(ErrorCode.SHARE_ALREADY_EXISTS);
            }
            share.setAllTenants(true);
        } else {
            UUID effectiveTenantId = command.actorRole() == RoleCode.SUPER_ADMIN
                    ? command.targetTenantId()
                    : actorTenantId;
            if (command.actorRole() == RoleCode.SUPER_ADMIN) {
                support.validateTargetTenantExists(effectiveTenantId);
            }
            if (shareRepository.existsActiveTenantFolderShare(folder.getId(), effectiveTenantId)) {
                throw new AppException(ErrorCode.SHARE_ALREADY_EXISTS);
            }
            share.setSharedWithTenantId(effectiveTenantId);
        }

        return support.toShareView(shareRepository.save(share));
    }
}
