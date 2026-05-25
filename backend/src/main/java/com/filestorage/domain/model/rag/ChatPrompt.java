package com.filestorage.domain.model.rag;

import java.util.List;

public record ChatPrompt(List<ChatPromptMessage> messages) {

    public ChatPrompt {
        messages = List.copyOf(messages);
    }
}
