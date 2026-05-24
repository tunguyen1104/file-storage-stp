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
import com.filestorage.domain.model.rag.ChatCompletion;
import com.filestorage.domain.model.rag.ChatPrompt;
import com.filestorage.domain.model.rag.ChatPromptMessage;
import com.filestorage.domain.model.rag.RagChatView;
import com.filestorage.domain.model.rag.RagRetrievalRequest;
import com.filestorage.domain.model.rag.RagRetrievedChunk;
import com.filestorage.domain.port.in.rag.ChatRagUseCase;
import com.filestorage.domain.port.out.ChatModelPort;
import com.filestorage.infrastructure.config.RagProperties;
import com.filestorage.shared.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChatRagServiceTest {

    @Mock
    private RagChatSessionService sessionService;
    @Mock
    private RagChatMessageJpaRepository messageRepository;
    @Mock
    private RagChatMessageSourceJpaRepository sourceRepository;
    @Mock
    private RagChatSessionJpaRepository sessionRepository;
    @Mock
    private RagRetrievalService retrievalService;
    @Mock
    private RagPromptBuilder promptBuilder;
    @Mock
    private ChatModelPort chatModelPort;

    private ChatRagService service;

    @BeforeEach
    void setUp() {
        service = new ChatRagService(
                sessionService,
                messageRepository,
                sourceRepository,
                sessionRepository,
                retrievalService,
                promptBuilder,
                chatModelPort,
                new RagProperties(),
                new RagChatMapper()
        );
    }

    @Test
    void noRetrievedChunksSavesDefaultAssistantAnswerWithoutCallingChatModel() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        RagChatSession session = session(sessionId, tenantId, userId);

        when(sessionService.loadActiveSession(sessionId, tenantId, userId)).thenReturn(session);
        when(messageRepository.findRecentBySessionId(any(), any(Pageable.class))).thenReturn(List.of());
        when(messageRepository.save(any(RagChatMessage.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));
        when(retrievalService.retrieve(any(RagRetrievalRequest.class))).thenReturn(List.of());
        when(sessionRepository.save(session)).thenReturn(session);

        RagChatView view = service.execute(new ChatRagUseCase.ChatRagCommand(
                userId,
                tenantId,
                RoleCode.MEMBER,
                sessionId,
                "What is in my documents?",
                null
        ));

        assertEquals(RagChatMessageRole.USER, view.userMessage().role());
        assertEquals(RagChatMessageRole.ASSISTANT, view.assistantMessage().role());
        assertTrue(view.assistantMessage().content().startsWith("Toi khong tim thay"));
        verify(chatModelPort, never()).complete(any());
        verify(sourceRepository, never()).saveAll(any());
    }

    @Test
    @SuppressWarnings({"unchecked", "rawtypes"})
    void retrievedChunksCallChatModelAndSaveSources() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        RagChatSession session = session(sessionId, tenantId, userId);
        RagRetrievedChunk chunk = new RagRetrievedChunk(
                "chunk-1",
                fileId,
                tenantId,
                null,
                userId,
                "policy.txt",
                2,
                "Company policy content",
                "contents/t/u/file.txt",
                0.91,
                0.8,
                0.7
        );
        ChatPrompt prompt = new ChatPrompt(List.of(new ChatPromptMessage("user", "prompt")));

        when(sessionService.loadActiveSession(sessionId, tenantId, userId)).thenReturn(session);
        when(messageRepository.findRecentBySessionId(any(), any(Pageable.class))).thenReturn(List.of());
        when(messageRepository.save(any(RagChatMessage.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));
        when(retrievalService.retrieve(any(RagRetrievalRequest.class))).thenReturn(List.of(chunk));
        when(promptBuilder.build("What is the policy?", List.of(chunk), List.of())).thenReturn(prompt);
        when(chatModelPort.complete(prompt)).thenReturn(new ChatCompletion("Answer", "alibaba-qwen3-32b", 12, 4));
        when(sourceRepository.saveAll(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(sessionRepository.save(session)).thenReturn(session);

        RagChatView view = service.execute(new ChatRagUseCase.ChatRagCommand(
                userId,
                tenantId,
                RoleCode.MEMBER,
                sessionId,
                " What is the policy? ",
                5
        ));

        assertEquals("Answer", view.assistantMessage().content());
        assertEquals("What is the policy?", session.getTitle());
        assertEquals(1, view.assistantMessage().sources().size());
        assertEquals(fileId, view.assistantMessage().sources().get(0).fileId());

        ArgumentCaptor<Iterable<RagChatMessageSource>> captor = ArgumentCaptor.forClass(Iterable.class);
        verify(sourceRepository).saveAll(captor.capture());
        RagChatMessageSource savedSource = captor.getValue().iterator().next();
        assertEquals(fileId, savedSource.getFileId());
        assertEquals("policy.txt", savedSource.getOriginalName());
        assertEquals(2, savedSource.getChunkIndex());
    }

    @Test
    void followUpQuestionUsesPreviousUserQuestionForRetrieval() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        RagChatSession session = session(sessionId, tenantId, userId);
        RagChatMessage previousUserMessage = message(sessionId, RagChatMessageRole.USER, "Input cua Ladder Game");
        String followUpQuestion = "Chi ti\u1ebft h\u01a1n";

        when(sessionService.loadActiveSession(sessionId, tenantId, userId)).thenReturn(session);
        when(messageRepository.findRecentBySessionId(any(), any(Pageable.class))).thenReturn(List.of(previousUserMessage));
        when(messageRepository.save(any(RagChatMessage.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));
        when(retrievalService.retrieve(any(RagRetrievalRequest.class))).thenReturn(List.of());
        when(sessionRepository.save(session)).thenReturn(session);

        service.execute(new ChatRagUseCase.ChatRagCommand(
                userId,
                tenantId,
                RoleCode.MEMBER,
                sessionId,
                followUpQuestion,
                5
        ));

        ArgumentCaptor<RagRetrievalRequest> captor = ArgumentCaptor.forClass(RagRetrievalRequest.class);
        verify(retrievalService).retrieve(captor.capture());
        assertEquals("Input cua Ladder Game\n" + followUpQuestion, captor.getValue().question());
    }

    @Test
    void shortStandaloneQuestionUsesOriginalTextForRetrieval() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        RagChatSession session = session(sessionId, tenantId, userId);
        RagChatMessage previousUserMessage = message(sessionId, RagChatMessageRole.USER, "Input cua Ladder Game");

        when(sessionService.loadActiveSession(sessionId, tenantId, userId)).thenReturn(session);
        when(messageRepository.findRecentBySessionId(any(), any(Pageable.class))).thenReturn(List.of(previousUserMessage));
        when(messageRepository.save(any(RagChatMessage.class))).thenAnswer(invocation -> withId(invocation.getArgument(0)));
        when(retrievalService.retrieve(any(RagRetrievalRequest.class))).thenReturn(List.of());
        when(sessionRepository.save(session)).thenReturn(session);

        service.execute(new ChatRagUseCase.ChatRagCommand(
                userId,
                tenantId,
                RoleCode.MEMBER,
                sessionId,
                "Ladder input",
                5
        ));

        ArgumentCaptor<RagRetrievalRequest> captor = ArgumentCaptor.forClass(RagRetrievalRequest.class);
        verify(retrievalService).retrieve(captor.capture());
        assertEquals("Ladder input", captor.getValue().question());
    }

    @Test
    void topKAboveConfiguredMaximumIsRejectedBeforeSavingMessages() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();

        assertThrows(AppException.class, () -> service.execute(new ChatRagUseCase.ChatRagCommand(
                userId,
                tenantId,
                RoleCode.MEMBER,
                UUID.randomUUID(),
                "Question",
                21
        )));

        verifyNoInteractions(sessionService, messageRepository, retrievalService, chatModelPort);
    }

    @Test
    void messageAboveConfiguredMaximumIsRejectedBeforeSavingMessages() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        String longMessage = "a".repeat(8001);

        assertThrows(AppException.class, () -> service.execute(new ChatRagUseCase.ChatRagCommand(
                userId,
                tenantId,
                RoleCode.MEMBER,
                UUID.randomUUID(),
                longMessage,
                null
        )));

        verifyNoInteractions(sessionService, messageRepository, retrievalService, chatModelPort);
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

    private static RagChatMessage withId(RagChatMessage message) {
        message.setId(UUID.randomUUID());
        message.setCreatedAt(Instant.now());
        return message;
    }

    private static RagChatMessage message(UUID sessionId, RagChatMessageRole role, String content) {
        RagChatMessage message = RagChatMessage.builder()
                .sessionId(sessionId)
                .role(role)
                .content(content)
                .build();
        message.setId(UUID.randomUUID());
        message.setCreatedAt(Instant.now());
        return message;
    }
}
