package com.filestorage.application.usecase.rag;

import com.filestorage.adapter.out.persistence.entity.RagChatMessage;
import com.filestorage.domain.model.enums.RagChatMessageRole;
import com.filestorage.domain.model.rag.ChatPrompt;
import com.filestorage.domain.model.rag.RagRetrievedChunk;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RagPromptBuilderTest {

    private final RagPromptBuilder builder = new RagPromptBuilder();

    @Test
    void buildAddsSystemHistoryAndSourceContext() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        RagChatMessage userHistory = message(RagChatMessageRole.USER, "Previous question");
        RagChatMessage assistantHistory = message(RagChatMessageRole.ASSISTANT, "Previous answer");
        RagRetrievedChunk chunk = new RagRetrievedChunk(
                "chunk-1",
                UUID.randomUUID(),
                tenantId,
                null,
                userId,
                "guide.txt",
                3,
                "Relevant content",
                "contents/t/u/f.txt",
                0.9,
                0.8,
                0.7
        );

        ChatPrompt prompt = builder.build("Current question", List.of(chunk), List.of(userHistory, assistantHistory));

        assertEquals(4, prompt.messages().size());
        assertEquals("system", prompt.messages().get(0).role());
        assertTrue(prompt.messages().get(0).content().contains("untrusted context"));
        assertEquals("user", prompt.messages().get(1).role());
        assertEquals("Previous question", prompt.messages().get(1).content());
        assertEquals("assistant", prompt.messages().get(2).role());
        assertEquals("Previous answer", prompt.messages().get(2).content());
        assertTrue(prompt.messages().get(3).content().contains("File: guide.txt"));
        assertTrue(prompt.messages().get(3).content().contains("Chunk: 3"));
        assertTrue(prompt.messages().get(3).content().contains("Current question"));
    }

    @Test
    void blankHistoryMessagesAreSkipped() {
        RagChatMessage blank = message(RagChatMessageRole.USER, " ");

        ChatPrompt prompt = builder.build("Question", List.of(), List.of(blank));

        assertEquals(2, prompt.messages().size());
        assertTrue(prompt.messages().get(1).content().contains("Khong co doan tai lieu phu hop"));
    }

    private static RagChatMessage message(RagChatMessageRole role, String content) {
        return RagChatMessage.builder()
                .role(role)
                .content(content)
                .build();
    }
}
