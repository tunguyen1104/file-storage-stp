package com.filestorage.domain.port.out;

import com.filestorage.domain.model.rag.EmbeddingVector;

import java.util.List;

public interface EmbeddingPort {

    List<EmbeddingVector> embed(List<String> texts);
}
