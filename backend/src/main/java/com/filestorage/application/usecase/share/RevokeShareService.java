package com.filestorage.application.usecase.share;

import com.filestorage.adapter.out.persistence.entity.FileShare;
import com.filestorage.adapter.out.persistence.entity.User;
import com.filestorage.domain.model.enums.ShareStatus;
import com.filestorage.domain.port.in.share.RevokeShareUseCase;
import com.filestorage.domain.port.out.ShareRepositoryPort;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RevokeShareService implements RevokeShareUseCase {

    private final ShareRepositoryPort shareRepository;
    private final ShareServiceSupport support;

    @Override
    @Transactional
    public void revokeShare(UUID shareId, UUID actorId) {
        User actor = support.resolveActor(actorId);
        FileShare share = shareRepository.findById(shareId)
                .orElseThrow(() -> new AppException(ErrorCode.SHARE_NOT_FOUND));
        validateActorOwnsSharedResource(actor, share);
        revoke(share);
        shareRepository.save(share);
    }

    private void validateActorOwnsSharedResource(User actor, FileShare share) {
        if (share.getFileId() != null) {
            ShareAccessPolicy.validateOwnership(support.fileOwnerId(support.findShareFile(share)), actor.getId());
            return;
        }
        ShareAccessPolicy.validateOwnership(support.folderOwnerId(support.findShareFolder(share)), actor.getId());
    }

    private void revoke(FileShare share) {
        Instant now = Instant.now();
        share.setStatus(ShareStatus.REVOKED);
        share.setRevokedAt(now);
        share.setUpdatedAt(now);
    }
}
