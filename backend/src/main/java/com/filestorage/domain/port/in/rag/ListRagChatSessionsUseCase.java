package com.filestorage.domain.port.in.rag;

import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.rag.RagChatSessionPageView;

import java.util.UUID;

public interface ListRagChatSessionsUseCase {

    RagChatSessionPageView execute(ListRagChatSessionsQuery query);

    record ListRagChatSessionsQuery(
            UUID actorUserId,
            UUID actorTenantId,
            RoleCode actorRole,
            int page,
            int size
    ) {
    }
}
