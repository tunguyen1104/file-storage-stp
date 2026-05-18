package com.filestorage.domain.port.in.share;

import java.util.UUID;

public interface RevokeShareUseCase {

    void revokeShare(UUID shareId, UUID actorId);
}
