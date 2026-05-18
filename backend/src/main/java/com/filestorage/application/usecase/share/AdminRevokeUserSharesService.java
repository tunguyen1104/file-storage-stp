package com.filestorage.application.usecase.share;

import com.filestorage.adapter.out.persistence.entity.FileShare;
import com.filestorage.adapter.out.persistence.entity.User;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.enums.ShareStatus;
import com.filestorage.domain.model.share.AdminBulkRevokeResultView;
import com.filestorage.domain.port.in.share.AdminRevokeUserSharesUseCase;
import com.filestorage.domain.port.out.ShareRepositoryPort;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class AdminRevokeUserSharesService implements AdminRevokeUserSharesUseCase {

    private static final String SCOPE_GRANTED = "GRANTED";
    private static final String SCOPE_RECEIVED = "RECEIVED";
    private static final String SCOPE_BOTH = "BOTH";

    private final ShareRepositoryPort shareRepository;
    private final ShareServiceSupport support;

    @Override
    @Transactional
    public AdminBulkRevokeResultView adminRevokeUserShares(UUID targetUserId, UUID adminId, String scope) {
        User admin = support.resolveActor(adminId);
        requireAdmin(admin);
        User target = support.resolveTargetUserById(targetUserId);
        validateAdminTargetScope(admin, target);

        String normalizedScope = normalizeScope(scope);
        List<FileShare> shares = shareRepository.findActiveSharesByUserScope(targetUserId, normalizedScope)
                .stream()
                .filter(share -> canAdminRevoke(admin, share))
                .toList();

        Instant now = Instant.now();
        shares.forEach(share -> {
            share.setStatus(ShareStatus.REVOKED);
            share.setRevokedAt(now);
            share.setUpdatedAt(now);
            shareRepository.save(share);
        });

        List<UUID> revokedShareIds = shares.stream().map(FileShare::getId).toList();
        return new AdminBulkRevokeResultView(
                targetUserId,
                normalizedScope,
                revokedShareIds,
                revokedShareIds.size(),
                null
        );
    }

    private void requireAdmin(User admin) {
        RoleCode role = admin.getRole() == null ? null : admin.getRole().getCode();
        if (role != RoleCode.TENANT_ADMIN && role != RoleCode.SUPER_ADMIN) {
            throw new AppException(ErrorCode.FORBIDDEN_ADMIN_REQUIRED);
        }
    }

    private void validateAdminTargetScope(User admin, User target) {
        if (isSuperAdmin(admin)) {
            return;
        }
        if (support.tenantId(admin) == null || !support.tenantId(admin).equals(support.tenantId(target))) {
            throw new AppException(ErrorCode.FORBIDDEN_ADMIN_REQUIRED);
        }
    }

    private boolean canAdminRevoke(User admin, FileShare share) {
        return isSuperAdmin(admin) || support.tenantId(admin).equals(share.getTenantId());
    }

    private boolean isSuperAdmin(User admin) {
        return admin.getRole() != null && admin.getRole().getCode() == RoleCode.SUPER_ADMIN;
    }

    private String normalizeScope(String scope) {
        if (scope == null || scope.isBlank()) {
            return SCOPE_BOTH;
        }
        String normalized = scope.trim().toUpperCase(Locale.ROOT);
        if (!SCOPE_GRANTED.equals(normalized) && !SCOPE_RECEIVED.equals(normalized) && !SCOPE_BOTH.equals(normalized)) {
            throw new AppException(ErrorCode.INVALID_PAYLOAD);
        }
        return normalized;
    }
}
