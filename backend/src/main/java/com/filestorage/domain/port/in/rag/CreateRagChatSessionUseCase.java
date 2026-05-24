package com.filestorage.domain.port.in.rag;

import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.rag.RagChatSessionView;

import java.util.UUID;

public interface CreateRagChatSessionUseCase {

    RagChatSessionView execute(CreateRagChatSessionCommand command);

    record CreateRagChatSessionCommand(
            UUID actorUserId,
            UUID actorTenantId,
            RoleCode actorRole,
            String title
    ) {
    }
}
