package com.filestorage.application.usecase.rag;

import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.rag.EmbeddingVector;
import com.filestorage.domain.model.rag.RagAccessScope;
import com.filestorage.domain.model.rag.RagRetrievalRequest;
import com.filestorage.domain.port.out.EmbeddingPort;
import com.filestorage.domain.port.out.RagVectorIndexPort;
import com.filestorage.infrastructure.config.RagProperties;
import com.filestorage.shared.exception.AppException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RagRetrievalServiceTest {

    @Mock
    private RagAccessResolver accessResolver;
    @Mock
    private EmbeddingPort embeddingPort;
    @Mock
    private RagVectorIndexPort ragVectorIndexPort;

    private RagProperties ragProperties;
    private RagRetrievalService service;

    @BeforeEach
    void setUp() {
        ragProperties = new RagProperties();
        ragProperties.getRetrieval().setTopK(8);
        service = new RagRetrievalService(accessResolver, embeddingPort, ragVectorIndexPort, ragProperties);
    }

    @Test
    void retrieveEmbedsQuestionAndRunsHybridSearch() {
        UUID tenantId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        RagAccessScope scope = new RagAccessScope(tenantId, userId, RoleCode.MEMBER, List.of(), List.of());
        when(accessResolver.resolve(tenantId, userId, RoleCode.MEMBER)).thenReturn(scope);
        when(embeddingPort.embed(List.of("hello"))).thenReturn(List.of(new EmbeddingVector(0, List.of(0.1f, 0.2f))));

        service.retrieve(new RagRetrievalRequest(tenantId, userId, RoleCode.MEMBER, "hello", null));

        verify(ragVectorIndexPort).hybridSearch("hello", List.of(0.1f, 0.2f), scope, 8);
    }

    @Test
    void invalidQuestionIsRejected() {
        assertThrows(AppException.class, () ->
                service.retrieve(new RagRetrievalRequest(UUID.randomUUID(), UUID.randomUUID(), RoleCode.MEMBER, " ", 8)));
    }

    @Test
    void topKAboveConfiguredMaximumIsRejected() {
        assertThrows(AppException.class, () ->
                service.retrieve(new RagRetrievalRequest(
                        UUID.randomUUID(),
                        UUID.randomUUID(),
                        RoleCode.MEMBER,
                        "hello",
                        ragProperties.getRetrieval().getMaxTopK() + 1
                )));
    }
}
