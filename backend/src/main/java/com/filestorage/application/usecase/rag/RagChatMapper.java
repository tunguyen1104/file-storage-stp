package com.filestorage.application.usecase.rag;

import com.filestorage.adapter.out.persistence.entity.RagChatMessage;
import com.filestorage.adapter.out.persistence.entity.RagChatSession;
import com.filestorage.domain.model.rag.RagChatMessageView;
import com.filestorage.domain.model.rag.RagChatSessionView;
import com.filestorage.domain.model.rag.RagSourceView;
import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class RagChatMapper {

    public RagChatSessionView toSessionView(RagChatSession session) {
        return new RagChatSessionView(
                session.getId(),
                session.getTitle(),
                session.getStatus(),
                session.getCreatedAt(),
                session.getUpdatedAt(),
                session.getLastMessageAt(),
                session.getDeletedAt()
        );
    }

    public RagChatMessageView toMessageView(
            RagChatMessage message,
            List<RagSourceView> sources
    ) {
        return new RagChatMessageView(
                message.getId(),
                message.getRole(),
                message.getContent(),
                message.getModel(),
                message.getRetrievalTopK(),
                message.getInputTokens(),
                message.getOutputTokens(),
                message.getCreatedAt(),
                sources == null ? List.of() : sources
        );
    }
}
