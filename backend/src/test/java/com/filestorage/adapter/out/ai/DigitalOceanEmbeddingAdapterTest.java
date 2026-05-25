package com.filestorage.adapter.out.ai;

import com.filestorage.domain.model.rag.EmbeddingVector;
import com.filestorage.infrastructure.config.DigitalOceanAiProperties;
import com.filestorage.infrastructure.config.RagProperties;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.http.HttpMethod.POST;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

class DigitalOceanEmbeddingAdapterTest {

    @Test
    void embedSendsRequestAndMapsVectorsInInputOrder() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        DigitalOceanEmbeddingAdapter adapter = new DigitalOceanEmbeddingAdapter(
                aiProperties("secret"),
                ragProperties(3),
                builder
        );

        server.expect(requestTo("https://unit.test/v1/embeddings"))
                .andExpect(method(POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer secret"))
                .andExpect(jsonPath("$.model").value("bge-m3"))
                .andExpect(jsonPath("$.input[0]").value("first"))
                .andExpect(jsonPath("$.input[1]").value("second"))
                .andRespond(withSuccess("""
                        {
                          "data": [
                            {"index": 1, "embedding": [0.3, 0.4, 0.5]},
                            {"index": 0, "embedding": [0.0, 0.1, 0.2]}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        List<EmbeddingVector> vectors = adapter.embed(List.of("first", "second"));

        assertEquals(2, vectors.size());
        assertEquals(0, vectors.get(0).index());
        assertEquals(List.of(0.0f, 0.1f, 0.2f), vectors.get(0).values());
        assertEquals(1, vectors.get(1).index());
        assertEquals(List.of(0.3f, 0.4f, 0.5f), vectors.get(1).values());
        server.verify();
    }

    @Test
    void embeddingDimensionMismatchIsRejected() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        DigitalOceanEmbeddingAdapter adapter = new DigitalOceanEmbeddingAdapter(
                aiProperties("secret"),
                ragProperties(3),
                builder
        );
        server.expect(requestTo("https://unit.test/v1/embeddings"))
                .andRespond(withSuccess("""
                        {
                          "data": [
                            {"index": 0, "embedding": [0.0, 0.1]}
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThrows(IllegalStateException.class, () -> adapter.embed(List.of("first")));
        server.verify();
    }

    @Test
    void blankTextIsRejectedBeforeHttpCall() {
        DigitalOceanEmbeddingAdapter adapter = new DigitalOceanEmbeddingAdapter(
                aiProperties("secret"),
                ragProperties(3),
                RestClient.builder()
        );

        assertThrows(IllegalArgumentException.class, () -> adapter.embed(List.of(" ")));
    }

    private static DigitalOceanAiProperties aiProperties(String apiKey) {
        DigitalOceanAiProperties properties = new DigitalOceanAiProperties();
        properties.setBaseUrl("https://unit.test/");
        properties.setApiKey(apiKey);
        return properties;
    }

    private static RagProperties ragProperties(int dimensions) {
        RagProperties properties = new RagProperties();
        properties.getEmbedding().setDimensions(dimensions);
        return properties;
    }
}
