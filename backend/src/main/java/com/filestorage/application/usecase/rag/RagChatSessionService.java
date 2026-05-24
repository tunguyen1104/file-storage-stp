package com.filestorage.application.usecase.rag;

import com.filestorage.adapter.out.persistence.entity.RagChatMessage;
import com.filestorage.adapter.out.persistence.entity.RagChatMessageSource;
import com.filestorage.adapter.out.persistence.entity.RagChatSession;
import com.filestorage.adapter.out.persistence.repository.RagChatMessageJpaRepository;
import com.filestorage.adapter.out.persistence.repository.RagChatMessageSourceJpaRepository;
import com.filestorage.adapter.out.persistence.repository.RagChatSessionJpaRepository;
import com.filestorage.domain.model.enums.RagChatMessageRole;
import com.filestorage.domain.model.enums.RagChatSessionStatus;
import com.filestorage.domain.model.rag.RagChatMessagePageView;
import com.filestorage.domain.model.rag.RagChatMessageView;
import com.filestorage.domain.model.rag.RagChatSessionPageView;
import com.filestorage.domain.model.rag.RagChatSessionView;
import com.filestorage.domain.model.rag.RagDeletedSessionView;
import com.filestorage.domain.model.rag.RagSourceView;
import com.filestorage.domain.port.in.rag.CreateRagChatSessionUseCase;
import com.filestorage.domain.port.in.rag.DeleteRagChatSessionUseCase;
import com.filestorage.domain.port.in.rag.GetRagChatMessagesUseCase;
import com.filestorage.domain.port.in.rag.ListRagChatSessionsUseCase;
import com.filestorage.domain.port.in.rag.RenameRagChatSessionUseCase;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RagChatSessionService implements
        CreateRagChatSessionUseCase,
        ListRagChatSessionsUseCase,
        GetRagChatMessagesUseCase,
        RenameRagChatSessionUseCase,
        DeleteRagChatSessionUseCase {

    private static final String DEFAULT_TITLE = "New chat";
    private static final int MAX_PAGE_SIZE = 100;

    private final RagChatSessionJpaRepository sessionRepository;
    private final RagChatMessageJpaRepository messageRepository;
    private final RagChatMessageSourceJpaRepository sourceRepository;
    private final RagChatMapper mapper;

    @Override
    @Transactional
    public RagChatSessionView execute(CreateRagChatSessionCommand command) {
        if (command == null) {
            throw new AppException(ErrorCode.INVALID_PAYLOAD);
        }
        validateActor(command.actorTenantId(), command.actorUserId());
        RagChatSession session = RagChatSession.builder()
                .tenantId(command.actorTenantId())
                .userId(command.actorUserId())
                .title(normalizeTitle(command.title()))
                .status(RagChatSessionStatus.ACTIVE)
                .build();
        return mapper.toSessionView(sessionRepository.save(session));
    }

    @Override
    @Transactional(readOnly = true)
    public RagChatSessionPageView execute(ListRagChatSessionsQuery query) {
        if (query == null) {
            throw new AppException(ErrorCode.INVALID_PAYLOAD);
        }
        validateActor(query.actorTenantId(), query.actorUserId());
        int page = Math.max(1, query.page());
        int size = normalizePageSize(query.size());
        Page<RagChatSession> sessions = sessionRepository.findByTenantIdAndUserIdAndStatusOrderByUpdatedAtDesc(
                query.actorTenantId(),
                query.actorUserId(),
                RagChatSessionStatus.ACTIVE,
                PageRequest.of(page - 1, size)
        );
        return new RagChatSessionPageView(
                sessions.getContent().stream().map(mapper::toSessionView).toList(),
                page,
                size,
                sessions.getTotalElements(),
                sessions.getTotalPages()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public RagChatMessagePageView execute(GetRagChatMessagesQuery query) {
        if (query == null || query.actorRole() == null) {
            throw new AppException(ErrorCode.INVALID_PAYLOAD);
        }
        validateActor(query.actorTenantId(), query.actorUserId());
        loadActiveSession(query.sessionId(), query.actorTenantId(), query.actorUserId());

        int page = Math.max(1, query.page());
        int size = normalizePageSize(query.size());
        Page<RagChatMessage> messages = messageRepository.findBySessionIdOrderByCreatedAtAsc(
                query.sessionId(),
                PageRequest.of(page - 1, size)
        );
        Map<UUID, List<RagChatMessageSource>> sourcesByMessageId = loadSourcesByMessageId(messages.getContent());
        List<RagChatMessageView> views = messages.getContent().stream()
                .map(message -> toMessageViewWithSources(
                        message,
                        sourcesByMessageId.getOrDefault(message.getId(), List.of())
                ))
                .toList();
        return new RagChatMessagePageView(views, page, size, messages.getTotalElements(), messages.getTotalPages());
    }

    @Override
    @Transactional
    public RagChatSessionView execute(RenameRagChatSessionCommand command) {
        if (command == null) {
            throw new AppException(ErrorCode.INVALID_PAYLOAD);
        }
        validateActor(command.actorTenantId(), command.actorUserId());
        if (command.title() == null || command.title().isBlank()) {
            throw new AppException(ErrorCode.INVALID_PAYLOAD);
        }
        RagChatSession session = loadActiveSession(command.sessionId(), command.actorTenantId(), command.actorUserId());
        session.setTitle(normalizeTitle(command.title()));
        return mapper.toSessionView(sessionRepository.save(session));
    }

    @Override
    @Transactional
    public RagDeletedSessionView execute(DeleteRagChatSessionCommand command) {
        if (command == null) {
            throw new AppException(ErrorCode.INVALID_PAYLOAD);
        }
        validateActor(command.actorTenantId(), command.actorUserId());
        RagChatSession session = loadActiveSession(command.sessionId(), command.actorTenantId(), command.actorUserId());
        hardDeleteSession(session);
        return new RagDeletedSessionView(session.getId(), true);
    }

    RagChatSession loadActiveSession(UUID sessionId, UUID tenantId, UUID userId) {
        if (sessionId == null) {
            throw new AppException(ErrorCode.INVALID_PAYLOAD);
        }
        return sessionRepository.findByIdAndTenantIdAndUserIdAndStatus(
                sessionId,
                tenantId,
                userId,
                RagChatSessionStatus.ACTIVE
        ).orElseThrow(() -> new AppException(ErrorCode.FORBIDDEN));
    }

    private Map<UUID, List<RagChatMessageSource>> loadSourcesByMessageId(List<RagChatMessage> messages) {
        if (messages.isEmpty()) {
            return Map.of();
        }
        List<UUID> assistantMessageIds = messages.stream()
                .filter(message -> message.getRole() == RagChatMessageRole.ASSISTANT)
                .map(RagChatMessage::getId)
                .toList();
        if (assistantMessageIds.isEmpty()) {
            return Map.of();
        }
        return sourceRepository.findByMessageIdInOrderByCreatedAtAsc(
                        assistantMessageIds
                )
                .stream()
                .collect(Collectors.groupingBy(RagChatMessageSource::getMessageId));
    }

    private RagChatMessageView toMessageViewWithSources(
            RagChatMessage message,
            List<RagChatMessageSource> sources
    ) {
        if (message.getRole() != RagChatMessageRole.ASSISTANT || sources.isEmpty()) {
            return mapper.toMessageView(message, List.of());
        }
        return mapper.toMessageView(message, sources.stream().map(this::toSourceView).toList());
    }

    private RagSourceView toSourceView(RagChatMessageSource source) {
        return new RagSourceView(
                source.getFileId(),
                source.getOriginalName(),
                source.getChunkIndex(),
                source.getSnippet(),
                source.getScore()
        );
    }

    private void hardDeleteSession(RagChatSession session) {
        List<UUID> messageIds = messageRepository.findIdsBySessionId(session.getId());
        if (!messageIds.isEmpty()) {
            sourceRepository.deleteByMessageIdIn(messageIds);
            messageRepository.deleteBySessionId(session.getId());
        }
        sessionRepository.delete(session);
    }

    private static void validateActor(UUID tenantId, UUID userId) {
        if (tenantId == null || userId == null) {
            throw new AppException(ErrorCode.INVALID_PAYLOAD);
        }
    }

    private static String normalizeTitle(String title) {
        if (title == null || title.isBlank()) {
            return DEFAULT_TITLE;
        }
        String trimmed = title.trim();
        return trimmed.length() <= 255 ? trimmed : trimmed.substring(0, 255);
    }

    private static int normalizePageSize(int size) {
        return Math.min(Math.max(1, size), MAX_PAGE_SIZE);
    }
}
