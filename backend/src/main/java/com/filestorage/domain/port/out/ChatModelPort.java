package com.filestorage.domain.port.out;

import com.filestorage.domain.model.rag.ChatCompletion;
import com.filestorage.domain.model.rag.ChatPrompt;

public interface ChatModelPort {

    ChatCompletion complete(ChatPrompt prompt);
}
