package com.filestorage.adapter.out.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.filestorage.domain.model.rag.ChatCompletion;
import com.filestorage.domain.model.rag.ChatPrompt;
import com.filestorage.domain.model.rag.ChatPromptMessage;
import com.filestorage.domain.port.out.ChatModelPort;
import com.filestorage.infrastructure.config.DigitalOceanAiProperties;
import com.filestorage.infrastructure.config.RagProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.List;

@Component
@ConditionalOnProperty(prefix = "app.rag", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DigitalOceanChatModelAdapter implements ChatModelPort {

    private final DigitalOceanAiProperties aiProperties;
    private final RagProperties ragProperties;
    private final RestClient restClient;

    public DigitalOceanChatModelAdapter(
            DigitalOceanAiProperties aiProperties,
            RagProperties ragProperties,
            RestClient.Builder restClientBuilder
    ) {
        this.aiProperties = aiProperties;
        this.ragProperties = ragProperties;
        this.restClient = restClientBuilder
                .baseUrl(trimTrailingSlash(aiProperties.getBaseUrl()))
                .build();
    }

    @Override
    public ChatCompletion complete(ChatPrompt prompt) {
        if (prompt == null || prompt.messages() == null || prompt.messages().isEmpty()) {
            throw new IllegalArgumentException("prompt messages must not be empty");
        }
        if (aiProperties.getApiKey() == null || aiProperties.getApiKey().isBlank()) {
            throw new IllegalStateException("app.digitalocean.ai.api-key is required for chat completions");
        }

        ChatCompletionResponse response = restClient.post()
                .uri("/v1/chat/completions")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + aiProperties.getApiKey())
                .body(new ChatCompletionRequest(
                        ragProperties.getChat().getModel(),
                        prompt.messages(),
                        ragProperties.getChat().getTemperature()
                ))
                .retrieve()
                .body(ChatCompletionResponse.class);

        if (response == null || response.choices() == null || response.choices().isEmpty()
                || response.choices().get(0).message() == null
                || response.choices().get(0).message().content() == null
                || response.choices().get(0).message().content().isBlank()) {
            throw new IllegalStateException("DigitalOcean chat completion response is empty");
        }

        Usage usage = response.usage();
        return new ChatCompletion(
                response.choices().get(0).message().content(),
                response.model() == null ? ragProperties.getChat().getModel() : response.model(),
                usage == null ? null : usage.promptTokens(),
                usage == null ? null : usage.completionTokens()
        );
    }

    private static String trimTrailingSlash(String value) {
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private record ChatCompletionRequest(
            String model,
            List<ChatPromptMessage> messages,
            double temperature
    ) {
    }

    private record ChatCompletionResponse(
            String model,
            List<Choice> choices,
            Usage usage
    ) {
    }

    private record Choice(ChatPromptMessage message) {
    }

    private record Usage(
            @JsonProperty("prompt_tokens") Integer promptTokens,
            @JsonProperty("completion_tokens") Integer completionTokens
    ) {
    }
}
