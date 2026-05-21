package com.filestorage.adapter.in.rest.dto.response;

import java.util.List;

public record SuggestResponse(
        List<SuggestHitResponse> items,
        long totalHits
) {
}
