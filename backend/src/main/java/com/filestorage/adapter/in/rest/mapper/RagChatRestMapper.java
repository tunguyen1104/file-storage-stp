package com.filestorage.adapter.in.rest.mapper;

import com.filestorage.adapter.in.rest.dto.response.PageResponse;
import com.filestorage.adapter.in.rest.dto.response.RagChatMessageResponse;
import com.filestorage.adapter.in.rest.dto.response.RagChatResponse;
import com.filestorage.adapter.in.rest.dto.response.RagChatSessionDeletedResponse;
import com.filestorage.adapter.in.rest.dto.response.RagChatSessionResponse;
import com.filestorage.adapter.in.rest.dto.response.RagSourceResponse;
import com.filestorage.domain.model.rag.RagChatMessagePageView;
import com.filestorage.domain.model.rag.RagChatMessageView;
import com.filestorage.domain.model.rag.RagChatSessionPageView;
import com.filestorage.domain.model.rag.RagChatSessionView;
import com.filestorage.domain.model.rag.RagChatView;
import com.filestorage.domain.model.rag.RagDeletedSessionView;
import com.filestorage.domain.model.rag.RagSourceView;
import org.springframework.stereotype.Component;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
public class RagChatRestMapper {

    public RagChatSessionResponse toSessionResponse(RagChatSessionView view) {
        return new RagChatSessionResponse(
                view.id(),
                view.title(),
                view.status(),
                view.createdAt(),
                view.updatedAt(),
                view.lastMessageAt(),
                view.deletedAt()
        );
    }

    public PageResponse<RagChatSessionResponse> toSessionPageResponse(RagChatSessionPageView view) {
        return PageResponse.<RagChatSessionResponse>builder()
                .data(view.data().stream().map(this::toSessionResponse).toList())
                .currentPage(view.page())
                .pageSize(view.size())
                .totalElements(view.total())
                .totalPages(view.totalPages())
                .build();
    }

    public RagChatMessageResponse toMessageResponse(RagChatMessageView view) {
        return new RagChatMessageResponse(
                view.id(),
                view.role(),
                view.content(),
                view.model(),
                view.retrievalTopK(),
                view.inputTokens(),
                view.outputTokens(),
                view.createdAt(),
                toSourceResponses(view.sources())
        );
    }

    public PageResponse<RagChatMessageResponse> toMessagePageResponse(RagChatMessagePageView view) {
        return PageResponse.<RagChatMessageResponse>builder()
                .data(view.data().stream().map(this::toMessageResponse).toList())
                .currentPage(view.page())
                .pageSize(view.size())
                .totalElements(view.total())
                .totalPages(view.totalPages())
                .build();
    }

    public RagChatResponse toChatResponse(RagChatView view) {
        return new RagChatResponse(
                toSessionResponse(view.session()),
                toMessageResponse(view.userMessage()),
                toMessageResponse(view.assistantMessage())
        );
    }

    public RagChatSessionDeletedResponse toDeletedSessionResponse(RagDeletedSessionView view) {
        return new RagChatSessionDeletedResponse(view.id(), view.deleted());
    }

    private List<RagSourceResponse> toSourceResponses(List<RagSourceView> sources) {
        Map<UUID, RagSourceResponse> uniqueSources = new LinkedHashMap<>();
        for (RagSourceView source : sources) {
            uniqueSources.putIfAbsent(source.fileId(), toSourceResponse(source));
        }
        return List.copyOf(uniqueSources.values());
    }

    private RagSourceResponse toSourceResponse(RagSourceView view) {
        return new RagSourceResponse(
                view.fileId(),
                view.originalName()
        );
    }
}
