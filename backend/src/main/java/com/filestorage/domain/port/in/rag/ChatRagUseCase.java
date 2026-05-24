package com.filestorage.domain.port.in.rag;

import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.rag.RagChatView;

import java.util.UUID;

public interface ChatRagUseCase {

    RagChatView execute(ChatRagCommand command);

    record ChatRagCommand(
            UUID actorUserId,
            UUID actorTenantId,
            RoleCode actorRole,
            UUID sessionId,
            String message,
            Integer topK
    ) {
    }
}
