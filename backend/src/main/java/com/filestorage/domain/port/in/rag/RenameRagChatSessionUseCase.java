package com.filestorage.domain.port.in.rag;

import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.rag.RagChatSessionView;

import java.util.UUID;

public interface RenameRagChatSessionUseCase {

    RagChatSessionView execute(RenameRagChatSessionCommand command);

    record RenameRagChatSessionCommand(
            UUID actorUserId,
            UUID actorTenantId,
            RoleCode actorRole,
            UUID sessionId,
            String title
    ) {
    }
}
