package com.filestorage.application.usecase.share;

import com.filestorage.adapter.out.persistence.entity.FileShare;
import com.filestorage.adapter.out.persistence.entity.User;
import com.filestorage.domain.model.enums.ShareStatus;
import com.filestorage.domain.model.share.ShareExpirationView;
import com.filestorage.domain.port.in.share.RenewShareExpirationUseCase;
import com.filestorage.domain.port.out.ShareRepositoryPort;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class RenewShareExpirationService implements RenewShareExpirationUseCase {

    private final ShareRepositoryPort shareRepository;
    private final ShareServiceSupport support;

    @Override
    @Transactional
    public ShareExpirationView renew(UUID shareId, UUID actorId, Duration duration) {
        User actor = support.resolveActor(actorId);
        FileShare share = findShare(shareId);
        validateActorOwnsSharedResource(actor, share);
        ShareAccessPolicy.validateNotRevoked(share);

        // ACTIVE: cộng dồn vào expiresAt cũ; EXPIRED: tính từ thời điểm hiện tại
        Instant base = share.getStatus() == ShareStatus.ACTIVE ? share.getExpiresAt() : Instant.now();
        Instant newExpiresAt = base.plus(duration);
        ShareAccessPolicy.validateExpiresAt(newExpiresAt);

        share.setExpiresAt(newExpiresAt);
        share.setStatus(ShareStatus.ACTIVE);
        share.setRevokedAt(null);
        share.setUpdatedAt(Instant.now());
        return support.toShareExpirationView(shareRepository.save(share));
    }

    private FileShare findShare(UUID shareId) {
        return shareRepository.findById(shareId)
                .orElseThrow(() -> new AppException(ErrorCode.SHARE_NOT_FOUND));
    }

    private void validateActorOwnsSharedResource(User actor, FileShare share) {
        if (share.getFileId() != null) {
            ShareAccessPolicy.validateOwnership(support.fileOwnerId(support.findShareFile(share)), actor.getId());
            return;
        }
        ShareAccessPolicy.validateOwnership(support.folderOwnerId(support.findShareFolder(share)), actor.getId());
    }
}
