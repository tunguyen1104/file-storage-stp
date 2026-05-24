package com.filestorage.application.usecase.rag;

import com.filestorage.domain.model.rag.EmbeddingVector;
import com.filestorage.domain.model.rag.RagAccessScope;
import com.filestorage.domain.model.rag.RagRetrievalRequest;
import com.filestorage.domain.model.rag.RagRetrievedChunk;
import com.filestorage.domain.port.out.EmbeddingPort;
import com.filestorage.domain.port.out.RagVectorIndexPort;
import com.filestorage.infrastructure.config.RagProperties;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class RagRetrievalService {

    private final RagAccessResolver accessResolver;
    private final EmbeddingPort embeddingPort;
    private final RagVectorIndexPort ragVectorIndexPort;
    private final RagProperties ragProperties;

    public List<RagRetrievedChunk> retrieve(RagRetrievalRequest request) {
        validate(request);

        RagAccessScope accessScope = accessResolver.resolve(request.tenantId(), request.userId(), request.role());
        List<EmbeddingVector> embeddings = embeddingPort.embed(List.of(request.question()));
        if (embeddings.size() != 1 || embeddings.get(0).index() != 0) {
            throw new IllegalStateException("Question embedding response is invalid");
        }

        int topK = request.topK() == null ? ragProperties.getRetrieval().getTopK() : request.topK();
        return ragVectorIndexPort.hybridSearch(request.question(), embeddings.get(0).values(), accessScope, topK);
    }

    private void validate(RagRetrievalRequest request) {
        if (request == null
                || request.question() == null
                || request.question().isBlank()
                || request.tenantId() == null
                || request.userId() == null
                || request.role() == null
                || isInvalidTopK(request.topK())) {
            throw new AppException(ErrorCode.INVALID_PAYLOAD);
        }
    }

    private boolean isInvalidTopK(Integer topK) {
        return topK != null && (topK < 1 || topK > ragProperties.getRetrieval().getMaxTopK());
    }
}
