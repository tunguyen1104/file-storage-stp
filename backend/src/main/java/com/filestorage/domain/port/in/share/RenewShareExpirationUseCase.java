package com.filestorage.domain.port.in.share;

import com.filestorage.domain.model.share.ShareExpirationView;

import java.time.Instant;
import java.util.UUID;

public interface RenewShareExpirationUseCase {

    ShareExpirationView renew(UUID shareId, UUID actorId, Instant expiresAt);
}
