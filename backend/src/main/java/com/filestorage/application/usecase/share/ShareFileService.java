package com.filestorage.application.usecase.share;

import com.filestorage.adapter.out.persistence.entity.File;
import com.filestorage.adapter.out.persistence.entity.FileShare;
import com.filestorage.adapter.out.persistence.entity.User;
import com.filestorage.domain.model.share.ShareView;
import com.filestorage.domain.port.in.share.ShareFileCommand;
import com.filestorage.domain.port.in.share.ShareFileUseCase;
import com.filestorage.domain.port.out.ShareRepositoryPort;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ShareFileService implements ShareFileUseCase {

    private final ShareRepositoryPort shareRepository;
    private final ShareServiceSupport support;

    @Override
    @Transactional
    public ShareView shareFile(ShareFileCommand command) {
        User actor = support.resolveActor(command.actorId(), command.actorTenantId());
        UUID actorTenantId = support.tenantId(actor);

        ShareAccessPolicy.validateExpiresAt(command.expiresAt());
        File file = support.findShareableFile(command.fileId(), actorTenantId);
        UUID ownerId = support.fileOwnerId(file);
        ShareAccessPolicy.validateOwnership(ownerId, actor.getId());

        User target = support.resolveTargetUserByEmail(command.sharedWithUserEmail());
        ShareAccessPolicy.validateNotSelf(ownerId, target.getId());
        ShareAccessPolicy.validateSameTenant(actorTenantId, support.tenantId(target));

        if (shareRepository.existsActiveUserFileShare(file.getId(), target.getId())) {
            throw new AppException(ErrorCode.SHARE_ALREADY_EXISTS);
        }

        FileShare share = support.newShare(actorTenantId, actor.getId(), command.expiresAt());
        share.setFileId(file.getId());
        share.setSharedWithUserId(target.getId());
        return support.toShareView(shareRepository.save(share));
    }
}
