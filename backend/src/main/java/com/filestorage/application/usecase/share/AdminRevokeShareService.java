package com.filestorage.application.usecase.share;

import com.filestorage.adapter.out.persistence.entity.FileShare;
import com.filestorage.adapter.out.persistence.entity.User;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.enums.ShareStatus;
import com.filestorage.domain.port.in.share.AdminRevokeShareUseCase;
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
public class AdminRevokeShareService implements AdminRevokeShareUseCase {

    private final ShareRepositoryPort shareRepository;
    private final ShareServiceSupport support;

    @Override
    @Transactional
    public void adminRevokeShare(UUID shareId, UUID adminId) {
        User admin = support.resolveActor(adminId);
        requireAdmin(admin);
        FileShare share = shareRepository.findById(shareId)
                .orElseThrow(() -> new AppException(ErrorCode.SHARE_NOT_FOUND));
        validateAdminScope(admin, share.getTenantId());
        revoke(share);
        shareRepository.save(share);
    }

    private void requireAdmin(User admin) {
        RoleCode role = admin.getRole() == null ? null : admin.getRole().getCode();
        if (role != RoleCode.TENANT_ADMIN && role != RoleCode.SUPER_ADMIN) {
            throw new AppException(ErrorCode.FORBIDDEN_ADMIN_REQUIRED);
        }
    }

    private void validateAdminScope(User admin, UUID shareTenantId) {
        RoleCode role = admin.getRole() == null ? null : admin.getRole().getCode();
        if (role == RoleCode.SUPER_ADMIN) {
            return;
        }
        if (support.tenantId(admin) == null || !support.tenantId(admin).equals(shareTenantId)) {
            throw new AppException(ErrorCode.FORBIDDEN_ADMIN_REQUIRED);
        }
    }

    private void revoke(FileShare share) {
        Instant now = Instant.now();
        share.setStatus(ShareStatus.REVOKED);
        share.setRevokedAt(now);
        share.setUpdatedAt(now);
    }
}
