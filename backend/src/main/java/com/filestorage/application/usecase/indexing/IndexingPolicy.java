package com.filestorage.application.usecase.indexing;

import com.filestorage.infrastructure.config.IndexingProperties;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Decides whether a file's MIME type is eligible for Tika text extraction.
 *
 * <p>The allowed list lives in {@code app.indexing.extractable-mime-types} —
 * no magic strings in code paths.
 */
@Component
@RequiredArgsConstructor
public class IndexingPolicy {

    private final IndexingProperties properties;

    public boolean shouldExtractContent(String mimeType) {
        if (mimeType == null || mimeType.isBlank()) {
            return false;
        }
        return normalizedSet().contains(mimeType.toLowerCase(Locale.ROOT));
    }

    private Set<String> normalizedSet() {
        return properties.getExtractableMimeTypes().stream()
                .map(s -> s.toLowerCase(Locale.ROOT))
                .collect(Collectors.toUnmodifiableSet());
    }
}
