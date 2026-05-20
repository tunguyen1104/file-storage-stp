package com.filestorage.domain.model.search;

import java.util.List;

public record SuggestView(
        List<SuggestHitView> items,
        long totalHits
) {
}
