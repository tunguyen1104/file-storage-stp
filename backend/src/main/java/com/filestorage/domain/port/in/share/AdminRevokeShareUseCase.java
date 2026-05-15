package com.filestorage.domain.port.in.share;

import java.util.UUID;

public interface AdminRevokeShareUseCase {

    void adminRevokeShare(UUID shareId, UUID adminId);
}
