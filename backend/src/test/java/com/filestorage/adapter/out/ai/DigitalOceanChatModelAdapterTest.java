package com.filestorage.adapter.out.ai;

import com.filestorage.domain.model.rag.ChatCompletion;
import com.filestorage.domain.model.rag.ChatPrompt;
import com.filestorage.domain.model.rag.ChatPromptMessage;
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
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.http.HttpMethod.POST;

class DigitalOceanChatModelAdapterTest {

    @Test
    void completeSendsChatRequestAndMapsResponse() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        DigitalOceanChatModelAdapter adapter = new DigitalOceanChatModelAdapter(
                aiProperties("secret"),
                new RagProperties(),
                builder
        );

        server.expect(requestTo("https://unit.test/v1/chat/completions"))
                .andExpect(method(POST))
                .andExpect(header(HttpHeaders.AUTHORIZATION, "Bearer secret"))
                .andExpect(jsonPath("$.model").value("alibaba-qwen3-32b"))
                .andExpect(jsonPath("$.messages[0].role").value("user"))
                .andExpect(jsonPath("$.messages[0].content").value("Question"))
                .andRespond(withSuccess("""
                        {
                          "model": "alibaba-qwen3-32b",
                          "choices": [
                            {
                              "message": {
                                "role": "assistant",
                                "content": "Answer"
                              }
                            }
                          ],
                          "usage": {
                            "prompt_tokens": 11,
                            "completion_tokens": 4
                          }
                        }
                        """, MediaType.APPLICATION_JSON));

        ChatCompletion completion = adapter.complete(new ChatPrompt(List.of(new ChatPromptMessage("user", "Question"))));

        assertEquals("Answer", completion.content());
        assertEquals("alibaba-qwen3-32b", completion.model());
        assertEquals(11, completion.inputTokens());
        assertEquals(4, completion.outputTokens());
        server.verify();
    }

    @Test
    void missingApiKeyIsRejectedBeforeHttpCall() {
        DigitalOceanChatModelAdapter adapter = new DigitalOceanChatModelAdapter(
                aiProperties(" "),
                new RagProperties(),
                RestClient.builder()
        );

        assertThrows(IllegalStateException.class, () ->
                adapter.complete(new ChatPrompt(List.of(new ChatPromptMessage("user", "Question")))));
    }

    @Test
    void blankAssistantContentIsRejected() {
        RestClient.Builder builder = RestClient.builder();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        DigitalOceanChatModelAdapter adapter = new DigitalOceanChatModelAdapter(
                aiProperties("secret"),
                new RagProperties(),
                builder
        );
        server.expect(requestTo("https://unit.test/v1/chat/completions"))
                .andRespond(withSuccess("""
                        {
                          "model": "alibaba-qwen3-32b",
                          "choices": [
                            {
                              "message": {
                                "role": "assistant",
                                "content": " "
                              }
                            }
                          ]
                        }
                        """, MediaType.APPLICATION_JSON));

        assertThrows(IllegalStateException.class, () ->
                adapter.complete(new ChatPrompt(List.of(new ChatPromptMessage("user", "Question")))));
        server.verify();
    }

    private static DigitalOceanAiProperties aiProperties(String apiKey) {
        DigitalOceanAiProperties properties = new DigitalOceanAiProperties();
        properties.setBaseUrl("https://unit.test/");
        properties.setApiKey(apiKey);
        return properties;
    }
}
