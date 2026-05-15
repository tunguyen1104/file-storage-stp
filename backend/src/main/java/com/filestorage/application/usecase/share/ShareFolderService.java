package com.filestorage.application.usecase.share;

import com.filestorage.adapter.out.persistence.entity.FileShare;
import com.filestorage.adapter.out.persistence.entity.Folder;
import com.filestorage.adapter.out.persistence.entity.User;
import com.filestorage.domain.model.share.ShareView;
import com.filestorage.domain.port.in.share.ShareFolderCommand;
import com.filestorage.domain.port.in.share.ShareFolderUseCase;
import com.filestorage.domain.port.out.ShareRepositoryPort;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ShareFolderService implements ShareFolderUseCase {

    private final ShareRepositoryPort shareRepository;
    private final ShareServiceSupport support;

    @Override
    @Transactional
    public ShareView shareFolder(ShareFolderCommand command) {
        User actor = support.resolveActor(command.actorId(), command.actorTenantId());
        UUID actorTenantId = support.tenantId(actor);

        ShareAccessPolicy.validateExpiresAt(command.expiresAt());
        if (command.permission() == null) {
            throw new AppException(ErrorCode.INVALID_PAYLOAD);
        }

        Folder folder = support.findShareableFolder(command.folderId(), actorTenantId);
        UUID ownerId = support.folderOwnerId(folder);
        ShareAccessPolicy.validateOwnership(ownerId, actor.getId());

        User target = support.resolveTargetUserByEmail(command.sharedWithUserEmail());
        ShareAccessPolicy.validateNotSelf(ownerId, target.getId());
        ShareAccessPolicy.validateSameTenant(actorTenantId, support.tenantId(target));

        if (shareRepository.existsActiveUserFolderShare(folder.getId(), target.getId())) {
            throw new AppException(ErrorCode.SHARE_ALREADY_EXISTS);
        }

        FileShare share = support.newShare(actorTenantId, actor.getId(), command.expiresAt());
        share.setFolderId(folder.getId());
        share.setSharedWithUserId(target.getId());
        share.setPermission(command.permission());
        return support.toShareView(shareRepository.save(share));
    }
}
