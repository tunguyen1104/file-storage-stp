package com.filestorage.domain.port.in.rag;

import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.rag.RagDeletedSessionView;

import java.util.UUID;

public interface DeleteRagChatSessionUseCase {

    RagDeletedSessionView execute(DeleteRagChatSessionCommand command);

    record DeleteRagChatSessionCommand(
            UUID actorUserId,
            UUID actorTenantId,
            RoleCode actorRole,
            UUID sessionId
    ) {
    }
}
