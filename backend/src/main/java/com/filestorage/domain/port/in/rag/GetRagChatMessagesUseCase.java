package com.filestorage.domain.port.in.rag;

import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.rag.RagChatMessagePageView;

import java.util.UUID;

public interface GetRagChatMessagesUseCase {

    RagChatMessagePageView execute(GetRagChatMessagesQuery query);

    record GetRagChatMessagesQuery(
            UUID actorUserId,
            UUID actorTenantId,
            RoleCode actorRole,
            UUID sessionId,
            int page,
            int size
    ) {
    }
}
