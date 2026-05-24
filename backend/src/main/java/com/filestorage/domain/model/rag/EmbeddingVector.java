package com.filestorage.domain.model.rag;

import java.util.List;
import java.util.Objects;

public record EmbeddingVector(
        int index,
        List<Float> values
) {

    public EmbeddingVector {
        if (index < 0) {
            throw new IllegalArgumentException("index must be non-negative");
        }
        values = List.copyOf(Objects.requireNonNull(values, "values must not be null"));
        if (values.isEmpty()) {
            throw new IllegalArgumentException("values must not be empty");
        }
    }
}
