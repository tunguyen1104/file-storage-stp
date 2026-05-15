package com.filestorage.domain.port.in.share;

import com.filestorage.domain.model.share.AdminBulkRevokeResultView;

import java.util.UUID;

public interface AdminRevokeUserSharesUseCase {

    AdminBulkRevokeResultView adminRevokeUserShares(UUID targetUserId, UUID adminId, String scope);
}
