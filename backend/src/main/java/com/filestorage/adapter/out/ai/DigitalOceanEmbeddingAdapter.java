package com.filestorage.adapter.out.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.filestorage.domain.model.rag.EmbeddingVector;
import com.filestorage.domain.port.out.EmbeddingPort;
import com.filestorage.infrastructure.config.DigitalOceanAiProperties;
import com.filestorage.infrastructure.config.RagProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.Comparator;
import java.util.List;
import java.util.Objects;

@Component
@ConditionalOnProperty(prefix = "app.rag", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DigitalOceanEmbeddingAdapter implements EmbeddingPort {

    private final DigitalOceanAiProperties aiProperties;
    private final RagProperties ragProperties;
    private final RestClient restClient;

    public DigitalOceanEmbeddingAdapter(
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
    public List<EmbeddingVector> embed(List<String> texts) {
        Objects.requireNonNull(texts, "texts must not be null");
        if (texts.isEmpty()) {
            return List.of();
        }
        for (String text : texts) {
            if (text == null || text.isBlank()) {
                throw new IllegalArgumentException("texts must not contain blank items");
            }
        }
        if (aiProperties.getApiKey() == null || aiProperties.getApiKey().isBlank()) {
            throw new IllegalStateException("app.digitalocean.ai.api-key is required for embeddings");
        }

        EmbeddingResponse response = restClient.post()
                .uri("/v1/embeddings")
                .contentType(MediaType.APPLICATION_JSON)
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + aiProperties.getApiKey())
                .body(new EmbeddingRequest(
                        ragProperties.getEmbedding().getModel(),
                        texts,
                        "float"
                ))
                .retrieve()
                .body(EmbeddingResponse.class);

        if (response == null || response.data() == null) {
            throw new IllegalStateException("DigitalOcean embedding response is empty");
        }

        List<EmbeddingData> data = response.data().stream()
                .sorted(Comparator.comparingInt(EmbeddingData::index))
                .toList();
        if (data.size() != texts.size()) {
            throw new IllegalStateException("DigitalOcean embedding response size does not match request size");
        }

        int dimensions = ragProperties.getEmbedding().getDimensions();
        return toVectors(data, dimensions);
    }

    private static List<EmbeddingVector> toVectors(List<EmbeddingData> data, int dimensions) {
        for (int i = 0; i < data.size(); i++) {
            if (data.get(i).index() != i) {
                throw new IllegalStateException("DigitalOcean embedding response index does not match request order");
            }
        }
        return data.stream()
                .map(item -> toVector(item, dimensions))
                .toList();
    }

    private static EmbeddingVector toVector(EmbeddingData item, int dimensions) {
        if (item.embedding() == null || item.embedding().size() != dimensions) {
            throw new IllegalStateException("Embedding dimension does not match app.rag.embedding.dimensions");
        }
        return new EmbeddingVector(item.index(), item.embedding());
    }

    private static String trimTrailingSlash(String value) {
        String trimmed = value.trim();
        while (trimmed.endsWith("/")) {
            trimmed = trimmed.substring(0, trimmed.length() - 1);
        }
        return trimmed;
    }

    private record EmbeddingRequest(
            String model,
            List<String> input,
            @JsonProperty("encoding_format") String encodingFormat
    ) {
    }

    private record EmbeddingResponse(List<EmbeddingData> data) {
    }

    private record EmbeddingData(int index, List<Float> embedding) {
    }
}
