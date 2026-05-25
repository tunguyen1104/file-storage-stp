package com.filestorage.application.usecase.rag;

import com.filestorage.adapter.out.persistence.entity.RagChatMessage;
import com.filestorage.adapter.out.persistence.entity.RagChatMessageSource;
import com.filestorage.adapter.out.persistence.entity.RagChatSession;
import com.filestorage.adapter.out.persistence.repository.RagChatMessageJpaRepository;
import com.filestorage.adapter.out.persistence.repository.RagChatMessageSourceJpaRepository;
import com.filestorage.adapter.out.persistence.repository.RagChatSessionJpaRepository;
import com.filestorage.domain.model.enums.RagChatMessageRole;
import com.filestorage.domain.model.enums.RagChatSessionStatus;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.rag.RagChatMessagePageView;
import com.filestorage.domain.model.rag.RagChatSessionPageView;
import com.filestorage.domain.model.rag.RagDeletedSessionView;
import com.filestorage.domain.port.in.rag.DeleteRagChatSessionUseCase;
import com.filestorage.domain.port.in.rag.GetRagChatMessagesUseCase;
import com.filestorage.domain.port.in.rag.ListRagChatSessionsUseCase;
import com.filestorage.shared.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagChatSessionServiceTest {

    @Mock
    private RagChatSessionJpaRepository sessionRepository;
    @Mock
    private RagChatMessageJpaRepository messageRepository;
    @Mock
    private RagChatMessageSourceJpaRepository sourceRepository;

    private RagChatSessionService service;

    @BeforeEach
    void setUp() {
        service = new RagChatSessionService(
                sessionRepository,
                messageRepository,
                sourceRepository,
                new RagChatMapper()
        );
    }

    @Test
    void listSessionsNormalizesPageAndClampsSize() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        RagChatSession session = session(UUID.randomUUID(), tenantId, userId);
        when(sessionRepository.findByTenantIdAndUserIdAndStatusOrderByUpdatedAtDesc(
                eq(tenantId),
                eq(userId),
                eq(RagChatSessionStatus.ACTIVE),
                argThat(pageable -> pageable.getPageNumber() == 0 && pageable.getPageSize() == 100)
        )).thenReturn(new PageImpl<>(List.of(session), PageRequest.of(0, 100), 1));

        RagChatSessionPageView view = service.execute(new ListRagChatSessionsUseCase.ListRagChatSessionsQuery(
                userId,
                tenantId,
                RoleCode.MEMBER,
                -1,
                500
        ));

        assertEquals(1, view.page());
        assertEquals(100, view.size());
        assertEquals(1, view.total());
    }

    @Test
    void getMessagesReturnsHistoricalSourceMetadataWithoutPermissionFiltering() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        UUID firstFileId = UUID.randomUUID();
        UUID secondFileId = UUID.randomUUID();
        RagChatMessage assistant = message(messageId, sessionId, RagChatMessageRole.ASSISTANT, "Answer");
        RagChatMessageSource firstSource = source(messageId, firstFileId, "first.txt");
        RagChatMessageSource secondSource = source(messageId, secondFileId, "second.txt");

        when(sessionRepository.findByIdAndTenantIdAndUserIdAndStatus(
                sessionId, tenantId, userId, RagChatSessionStatus.ACTIVE
        )).thenReturn(Optional.of(session(sessionId, tenantId, userId)));
        when(messageRepository.findBySessionIdOrderByCreatedAtAsc(eq(sessionId), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(assistant), PageRequest.of(0, 50), 1));
        when(sourceRepository.findByMessageIdInOrderByCreatedAtAsc(List.of(messageId)))
                .thenReturn(List.of(firstSource, secondSource));

        RagChatMessagePageView view = service.execute(new GetRagChatMessagesUseCase.GetRagChatMessagesQuery(
                userId,
                tenantId,
                RoleCode.MEMBER,
                sessionId,
                1,
                50
        ));

        assertEquals(1, view.data().size());
        assertEquals(2, view.data().get(0).sources().size());
        assertEquals(firstFileId, view.data().get(0).sources().get(0).fileId());
        assertEquals(secondFileId, view.data().get(0).sources().get(1).fileId());
    }

    @Test
    void nullQueryIsRejected() {
        assertThrows(AppException.class, () -> service.execute((ListRagChatSessionsUseCase.ListRagChatSessionsQuery) null));
    }

    @Test
    void deleteSessionHardDeletesSourcesMessagesAndSession() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID firstMessageId = UUID.randomUUID();
        UUID secondMessageId = UUID.randomUUID();
        RagChatSession session = session(sessionId, tenantId, userId);
        List<UUID> messageIds = List.of(firstMessageId, secondMessageId);

        when(sessionRepository.findByIdAndTenantIdAndUserIdAndStatus(
                sessionId, tenantId, userId, RagChatSessionStatus.ACTIVE
        )).thenReturn(Optional.of(session));
        when(messageRepository.findIdsBySessionId(sessionId)).thenReturn(messageIds);

        RagDeletedSessionView view = service.execute(new DeleteRagChatSessionUseCase.DeleteRagChatSessionCommand(
                userId,
                tenantId,
                RoleCode.MEMBER,
                sessionId
        ));

        assertEquals(sessionId, view.id());
        assertTrue(view.deleted());

        var order = inOrder(sourceRepository, messageRepository, sessionRepository);
        order.verify(sourceRepository).deleteByMessageIdIn(messageIds);
        order.verify(messageRepository).deleteBySessionId(sessionId);
        order.verify(sessionRepository).delete(session);
    }

    @Test
    void deleteEmptySessionHardDeletesSessionOnly() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        RagChatSession session = session(sessionId, tenantId, userId);

        when(sessionRepository.findByIdAndTenantIdAndUserIdAndStatus(
                sessionId, tenantId, userId, RagChatSessionStatus.ACTIVE
        )).thenReturn(Optional.of(session));
        when(messageRepository.findIdsBySessionId(sessionId)).thenReturn(List.of());

        service.execute(new DeleteRagChatSessionUseCase.DeleteRagChatSessionCommand(
                userId,
                tenantId,
                RoleCode.MEMBER,
                sessionId
        ));

        verify(sourceRepository, org.mockito.Mockito.never()).deleteByMessageIdIn(any());
        verify(messageRepository, org.mockito.Mockito.never()).deleteBySessionId(any());
        verify(sessionRepository).delete(session);
    }

    private static RagChatSession session(UUID sessionId, UUID tenantId, UUID userId) {
        RagChatSession session = RagChatSession.builder()
                .tenantId(tenantId)
                .userId(userId)
                .title("New chat")
                .status(RagChatSessionStatus.ACTIVE)
                .build();
        session.setId(sessionId);
        session.setCreatedAt(Instant.now());
        session.setUpdatedAt(Instant.now());
        return session;
    }

    private static RagChatMessage message(UUID messageId, UUID sessionId, RagChatMessageRole role, String content) {
        RagChatMessage message = RagChatMessage.builder()
                .sessionId(sessionId)
                .role(role)
                .content(content)
                .build();
        message.setId(messageId);
        message.setCreatedAt(Instant.now());
        return message;
    }

    private static RagChatMessageSource source(UUID messageId, UUID fileId, String originalName) {
        RagChatMessageSource source = RagChatMessageSource.builder()
                .messageId(messageId)
                .fileId(fileId)
                .originalName(originalName)
                .chunkIndex(0)
                .snippet("snippet")
                .score(0.9)
                .sourceKey("source-key")
                .build();
        source.setId(UUID.randomUUID());
        source.setCreatedAt(Instant.now());
        return source;
    }
}
