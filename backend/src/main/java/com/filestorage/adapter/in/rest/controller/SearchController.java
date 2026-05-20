package com.filestorage.adapter.in.rest.controller;

import com.filestorage.adapter.in.rest.dto.request.SearchRequest;
import com.filestorage.adapter.in.rest.dto.request.SuggestRequest;
import com.filestorage.adapter.in.rest.dto.response.ApiResponse;
import com.filestorage.adapter.in.rest.dto.response.SearchResponse;
import com.filestorage.adapter.in.rest.dto.response.SuggestResponse;
import com.filestorage.adapter.in.rest.mapper.SearchRestMapper;
import com.filestorage.adapter.in.rest.security.SecurityUser;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.enums.SortDirection;
import com.filestorage.domain.model.search.SearchSortBy;
import com.filestorage.domain.model.search.SearchType;
import com.filestorage.domain.model.search.SearchView;
import com.filestorage.domain.model.search.SuggestView;
import com.filestorage.domain.port.in.search.SearchQuery;
import com.filestorage.domain.port.in.search.SearchUseCase;
import com.filestorage.domain.port.in.search.SuggestQuery;
import com.filestorage.domain.port.in.search.SuggestUseCase;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import com.filestorage.shared.util.SecurityContextUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ModelAttribute;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Two endpoints — kept deliberately separate because the cache TTL, response
 * shape, and ES query shape diverge.
 *
 * <ul>
 *   <li>{@code GET /api/v1/search} — Enter-key fuzzy full-text search.</li>
 *   <li>{@code GET /api/v1/search/suggest} — SAYT autocomplete dropdown.</li>
 * </ul>
 *
 * <p>Both endpoints read {@code userId} / {@code tenantId} / {@code role} from
 * the JWT (via {@link SecurityUser}) — never from the request. The frontend
 * can't influence visibility filtering by spoofing query params.
 */
@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class SearchController {

    private final SearchUseCase searchUseCase;
    private final SuggestUseCase suggestUseCase;
    private final SearchRestMapper mapper;

    @GetMapping("/search")
    public ResponseEntity<ApiResponse<SearchResponse>> search(@ModelAttribute SearchRequest request) {
        SecurityUser actor = currentUserOrThrow();
        SearchView view = searchUseCase.execute(new SearchQuery(
                request.getQ(),
                UUID.fromString(actor.getUserId()),
                UUID.fromString(actor.getTenantId()),
                parseRole(actor.getRole()),
                request.getType() == null ? SearchType.ALL : request.getType(),
                request.getMimeType(),
                request.getDateFrom(),
                request.getDateTo(),
                request.getPage() == null ? 1 : request.getPage(),
                request.getSize() == null ? 20 : request.getSize(),
                request.getSort() == null ? SearchSortBy.SCORE : request.getSort(),
                request.getSortDir() == null ? SortDirection.DESC : request.getSortDir()
        ));
        return ResponseEntity.ok(ApiResponse.buildSuccess(mapper.toSearchResponse(view)));
    }

    @GetMapping("/search/suggest")
    public ResponseEntity<ApiResponse<SuggestResponse>> suggest(@ModelAttribute SuggestRequest request) {
        SecurityUser actor = currentUserOrThrow();
        SuggestView view = suggestUseCase.execute(new SuggestQuery(
                request.getQ(),
                UUID.fromString(actor.getUserId()),
                UUID.fromString(actor.getTenantId()),
                parseRole(actor.getRole()),
                request.getType() == null ? SearchType.ALL : request.getType(),
                request.getSize() == null ? 0 : request.getSize()
        ));
        return ResponseEntity.ok(ApiResponse.buildSuccess(mapper.toSuggestResponse(view)));
    }

    private SecurityUser currentUserOrThrow() {
        return SecurityContextUtil.getCurrentUser().orElseThrow(() -> new AppException(ErrorCode.ACCESS_DENIED));
    }

    private RoleCode parseRole(String role) {
        try {
            return RoleCode.valueOf(role);
        } catch (IllegalArgumentException ex) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }
    }
}
