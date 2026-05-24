package com.filestorage.adapter.in.rest.controller;

import com.filestorage.adapter.in.rest.advice.GlobalExceptionHandler;
import com.filestorage.adapter.in.rest.mapper.RagChatRestMapper;
import com.filestorage.adapter.in.rest.security.SecurityUser;
import com.filestorage.domain.model.enums.RagChatMessageRole;
import com.filestorage.domain.model.enums.RagChatSessionStatus;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.rag.RagChatMessagePageView;
import com.filestorage.domain.model.rag.RagChatMessageView;
import com.filestorage.domain.model.rag.RagChatSessionView;
import com.filestorage.domain.model.rag.RagChatView;
import com.filestorage.domain.model.rag.RagDeletedSessionView;
import com.filestorage.domain.model.rag.RagSourceView;
import com.filestorage.domain.port.in.rag.ChatRagUseCase;
import com.filestorage.domain.port.in.rag.CreateRagChatSessionUseCase;
import com.filestorage.domain.port.in.rag.DeleteRagChatSessionUseCase;
import com.filestorage.domain.port.in.rag.GetRagChatMessagesUseCase;
import com.filestorage.domain.port.in.rag.ListRagChatSessionsUseCase;
import com.filestorage.domain.port.in.rag.RenameRagChatSessionUseCase;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.validation.beanvalidation.LocalValidatorFactoryBean;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@ExtendWith(MockitoExtension.class)
class RagChatControllerTest {

    @Mock
    private CreateRagChatSessionUseCase createSessionUseCase;
    @Mock
    private ListRagChatSessionsUseCase listSessionsUseCase;
    @Mock
    private GetRagChatMessagesUseCase getMessagesUseCase;
    @Mock
    private RenameRagChatSessionUseCase renameSessionUseCase;
    @Mock
    private DeleteRagChatSessionUseCase deleteSessionUseCase;
    @Mock
    private ChatRagUseCase chatRagUseCase;

    private MockMvc mockMvc;
    private UUID actorUserId;
    private UUID actorTenantId;

    @BeforeEach
    void setUp() {
        actorUserId = UUID.randomUUID();
        actorTenantId = UUID.randomUUID();
        authenticate("MEMBER");

        var messageSource = new ResourceBundleMessageSource();
        messageSource.setBasename("messages");
        messageSource.setDefaultEncoding("UTF-8");

        var validator = new LocalValidatorFactoryBean();
        validator.afterPropertiesSet();

        mockMvc = MockMvcBuilders.standaloneSetup(new RagChatController(
                        createSessionUseCase,
                        listSessionsUseCase,
                        getMessagesUseCase,
                        renameSessionUseCase,
                        deleteSessionUseCase,
                        chatRagUseCase,
                        new RagChatRestMapper()
                ))
                .setControllerAdvice(new GlobalExceptionHandler(messageSource))
                .setValidator(validator)
                .build();
    }

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    @Test
    void createSessionReturns201() throws Exception {
        UUID sessionId = UUID.randomUUID();
        Instant now = Instant.now();
        when(createSessionUseCase.execute(argThat(command ->
                command.actorUserId().equals(actorUserId)
                        && command.actorTenantId().equals(actorTenantId)
                        && command.actorRole() == RoleCode.MEMBER
                        && command.title().equals("Policy questions")
        ))).thenReturn(new RagChatSessionView(
                sessionId,
                "Policy questions",
                RagChatSessionStatus.ACTIVE,
                now,
                now,
                null,
                null
        ));

        mockMvc.perform(post("/api/v1/rag/chat/sessions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"title\":\"Policy questions\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(sessionId.toString()))
                .andExpect(jsonPath("$.data.title").value("Policy questions"));
    }

    @Test
    void chatReturnsAssistantAnswerAndSources() throws Exception {
        UUID sessionId = UUID.randomUUID();
        UUID userMessageId = UUID.randomUUID();
        UUID assistantMessageId = UUID.randomUUID();
        UUID fileId = UUID.randomUUID();
        Instant now = Instant.now();
        RagChatSessionView session = new RagChatSessionView(
                sessionId,
                "Question",
                RagChatSessionStatus.ACTIVE,
                now,
                now,
                now,
                null
        );
        RagSourceView source = new RagSourceView(fileId, "guide.txt", 0, "snippet", 0.9);
        RagSourceView duplicateSource = new RagSourceView(fileId, "guide.txt", 1, "another snippet", 0.8);
        RagChatView chatView = new RagChatView(
                session,
                new RagChatMessageView(userMessageId, RagChatMessageRole.USER, "Question", null, null, null, null, now, List.of()),
                new RagChatMessageView(assistantMessageId, RagChatMessageRole.ASSISTANT, "Answer", "alibaba-qwen3-32b", 8, 10, 4, now, List.of(source, duplicateSource))
        );
        when(chatRagUseCase.execute(argThat(command ->
                command.actorUserId().equals(actorUserId)
                        && command.actorTenantId().equals(actorTenantId)
                        && command.sessionId().equals(sessionId)
                        && command.message().equals("Question")
                        && command.topK() == 8
        ))).thenReturn(chatView);

        mockMvc.perform(post("/api/v1/rag/chat/sessions/{sessionId}/messages", sessionId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\"Question\",\"topK\":8}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.assistantMessage.content").value("Answer"))
                .andExpect(jsonPath("$.data.sources").doesNotExist())
                .andExpect(jsonPath("$.data.hiddenSourceCount").doesNotExist())
                .andExpect(jsonPath("$.data.userMessage.hiddenSourceCount").doesNotExist())
                .andExpect(jsonPath("$.data.assistantMessage.hiddenSourceCount").doesNotExist())
                .andExpect(jsonPath("$.data.assistantMessage.sources[0].fileId").value(fileId.toString()))
                .andExpect(jsonPath("$.data.assistantMessage.sources[0].originalName").value("guide.txt"))
                .andExpect(jsonPath("$.data.assistantMessage.sources[0].chunkIndex").doesNotExist())
                .andExpect(jsonPath("$.data.assistantMessage.sources[0].snippet").doesNotExist())
                .andExpect(jsonPath("$.data.assistantMessage.sources[0].score").doesNotExist())
                .andExpect(jsonPath("$.data.assistantMessage.sources[0].sourceKey").doesNotExist())
                .andExpect(jsonPath("$.data.assistantMessage.sources[1]").doesNotExist());
    }

    @Test
    void listMessagesDoesNotReturnHiddenSourceCount() throws Exception {
        UUID sessionId = UUID.randomUUID();
        UUID messageId = UUID.randomUUID();
        Instant now = Instant.now();
        when(getMessagesUseCase.execute(argThat(query ->
                query.actorUserId().equals(actorUserId)
                        && query.actorTenantId().equals(actorTenantId)
                        && query.sessionId().equals(sessionId)
                        && query.page() == 1
                        && query.size() == 50
        ))).thenReturn(new RagChatMessagePageView(
                List.of(new RagChatMessageView(
                        messageId,
                        RagChatMessageRole.ASSISTANT,
                        "Answer",
                        "alibaba-qwen3-32b",
                        8,
                        null,
                        null,
                        now,
                        List.of()
                )),
                1,
                50,
                1,
                1
        ));

        mockMvc.perform(get("/api/v1/rag/chat/sessions/{sessionId}/messages", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.data[0].hiddenSourceCount").doesNotExist());
    }

    @Test
    void deleteSessionReturnsHardDeletedResponse() throws Exception {
        UUID sessionId = UUID.randomUUID();
        when(deleteSessionUseCase.execute(argThat(command ->
                command.actorUserId().equals(actorUserId)
                        && command.actorTenantId().equals(actorTenantId)
                        && command.sessionId().equals(sessionId)
        ))).thenReturn(new RagDeletedSessionView(sessionId, true));

        mockMvc.perform(delete("/api/v1/rag/chat/sessions/{sessionId}", sessionId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.data.id").value(sessionId.toString()))
                .andExpect(jsonPath("$.data.deleted").value(true))
                .andExpect(jsonPath("$.data.status").doesNotExist())
                .andExpect(jsonPath("$.data.deletedAt").doesNotExist());
    }

    @Test
    void blankChatMessageReturns400() throws Exception {
        mockMvc.perform(post("/api/v1/rag/chat/sessions/{sessionId}/messages", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"message\":\" \"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void invalidActorRoleReturns401() throws Exception {
        authenticate("UNKNOWN");

        mockMvc.perform(get("/api/v1/rag/chat/sessions"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false));
    }

    private void authenticate(String role) {
        SecurityUser securityUser = new SecurityUser(
                actorUserId.toString(),
                actorTenantId.toString(),
                role,
                "member@example.com"
        );
        var auth = new UsernamePasswordAuthenticationToken(securityUser, null, securityUser.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }
}
