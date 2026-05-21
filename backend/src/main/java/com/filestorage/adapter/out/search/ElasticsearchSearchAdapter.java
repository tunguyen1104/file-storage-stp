package com.filestorage.adapter.out.search;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.SortOptions;
import co.elastic.clients.elasticsearch._types.SortOrder;
import co.elastic.clients.elasticsearch._types.aggregations.Aggregation;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.IdsQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.MultiMatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TermsQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TermsQueryField;
import co.elastic.clients.elasticsearch._types.query_dsl.TextQueryType;
import org.springframework.data.elasticsearch.core.query.HighlightQuery;
import org.springframework.data.elasticsearch.core.query.highlight.Highlight;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightField;
import org.springframework.data.elasticsearch.core.query.highlight.HighlightFieldParameters;
import com.filestorage.adapter.out.search.document.FileDocument;
import com.filestorage.adapter.out.search.document.FolderDocument;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.enums.SortDirection;
import com.filestorage.domain.model.search.SearchHitView;
import com.filestorage.domain.model.search.SearchSortBy;
import com.filestorage.domain.model.search.SearchType;
import com.filestorage.domain.model.search.SearchView;
import com.filestorage.domain.model.search.SuggestHitView;
import com.filestorage.domain.model.search.SuggestView;
import com.filestorage.domain.port.in.search.SearchQuery;
import com.filestorage.domain.port.in.search.SuggestQuery;
import com.filestorage.domain.port.out.FileIndexData;
import com.filestorage.domain.port.out.FolderIndexData;
import com.filestorage.domain.port.out.SearchIndexPort;
import com.filestorage.infrastructure.config.ElasticsearchProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.document.Document;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.FetchSourceFilter;
import org.springframework.data.elasticsearch.core.query.UpdateQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.client.elc.NativeQueryBuilder;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

/**
 * Elasticsearch implementation of {@link SearchIndexPort}.
 *
 * <p>Uses {@link ElasticsearchOperations} + native {@link NativeQuery} for all
 * operations — never {@code ElasticsearchRepository}, which obscures the query
 * shape and makes the visibility filter easy to bypass.
 *
 * <p>Every public search/suggest method routes through
 * {@link #applyVisibilityFilter} so the tenant + role + share filters are
 * applied in exactly one place. Adding a new search method without going
 * through this helper is an immediate review-blocker.
 *
 * <p>{@code type=ALL} runs two parallel queries (one against {@code files},
 * one against {@code folders}) and merges the hits by {@code _score}. A single
 * multi-index query would score files with a {@code content^1} clause that
 * folders never have, biasing the ranking unfairly against folders.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class ElasticsearchSearchAdapter implements SearchIndexPort {

    private final ElasticsearchOperations elasticsearchOperations;
    private final ElasticsearchProperties properties;

    private IndexCoordinates filesIndex() {
        return IndexCoordinates.of(properties.getIndices().getFiles());
    }

    private IndexCoordinates foldersIndex() {
        return IndexCoordinates.of(properties.getIndices().getFolders());
    }

    // ── Write operations ─────────────────────────────────────────────────────

    @Override
    public void indexFile(FileIndexData data) {
        FileDocument doc = FileDocument.builder()
                .id(data.id().toString())
                .tenantId(data.tenantId().toString())
                .folderId(data.folderId() == null ? null : data.folderId().toString())
                .uploadedBy(data.uploadedBy().toString())
                .originalName(data.originalName())
                .mimeType(data.mimeType())
                .content(data.content())
                .createdAt(data.createdAt())
                .build();
        elasticsearchOperations.save(doc, filesIndex());
        log.info("Indexed file doc id={} tenantId={} hasContent={}",
                data.id(), data.tenantId(), data.content() != null);
    }

    @Override
    public void updateFileMetadata(UUID fileId, String originalName, UUID folderId) {
        Map<String, Object> partial = new HashMap<>();
        partial.put("originalName", originalName);
        partial.put("folderId", folderId == null ? null : folderId.toString());
        UpdateQuery update = UpdateQuery.builder(fileId.toString())
                .withDocument(Document.from(partial))
                .withDocAsUpsert(false)
                .build();
        elasticsearchOperations.update(update, filesIndex());
        log.info("Updated file doc id={} fields=[originalName, folderId]", fileId);
    }

    @Override
    public void deleteFile(UUID fileId) {
        elasticsearchOperations.delete(fileId.toString(), filesIndex());
        log.info("Deleted file doc id={}", fileId);
    }

    @Override
    public void indexFolder(FolderIndexData data) {
        FolderDocument doc = FolderDocument.builder()
                .id(data.id().toString())
                .tenantId(data.tenantId().toString())
                .parentId(data.parentId() == null ? null : data.parentId().toString())
                .createdBy(data.createdBy().toString())
                .name(data.name())
                .createdAt(data.createdAt())
                .build();
        elasticsearchOperations.save(doc, foldersIndex());
        log.info("Indexed folder doc id={} tenantId={}", data.id(), data.tenantId());
    }

    @Override
    public void deleteFolder(UUID folderId) {
        elasticsearchOperations.delete(folderId.toString(), foldersIndex());
        log.info("Deleted folder doc id={}", folderId);
    }

    // ── Read operations: full-text ───────────────────────────────────────────

    @Override
    public SearchView searchAll(SearchQuery query, List<UUID> sharedFileIds, List<UUID> sharedFolderIds) {
        SearchType type = query.type() == null ? SearchType.ALL : query.type();

        return switch (type) {
            case FILE   -> runFileSearch(query, sharedFileIds);
            case FOLDER -> runFolderSearch(query, sharedFolderIds);
            case ALL    -> runMergedSearch(query, sharedFileIds, sharedFolderIds);
        };
    }

    private SearchView runFileSearch(SearchQuery query, List<UUID> sharedFileIds) {
        NativeQuery nq = buildFileQuery(query, sharedFileIds, query.page(), query.size(), true);
        SearchHits<FileDocument> hits = elasticsearchOperations.search(nq, FileDocument.class, filesIndex());
        List<SearchHitView> items = mapFileHits(hits);
        Map<String, Map<String, Long>> aggs = extractMimeAggregation(hits);
        return new SearchView(items, hits.getTotalHits(), query.page(), query.size(), aggs);
    }

    private SearchView runFolderSearch(SearchQuery query, List<UUID> sharedFolderIds) {
        NativeQuery nq = buildFolderQuery(query, sharedFolderIds, query.page(), query.size());
        SearchHits<FolderDocument> hits = elasticsearchOperations.search(nq, FolderDocument.class, foldersIndex());
        List<SearchHitView> items = mapFolderHits(hits);
        return new SearchView(items, hits.getTotalHits(), query.page(), query.size(), Collections.emptyMap());
    }

    /**
     * Over-fetch strategy for paginated merge: we have to fetch
     * {@code page * size} candidates from each index, merge by score, then
     * slice the requested page. If we only fetched {@code size} from each, the
     * correct rank-30 file might never be pulled in when computing page 2.
     */
    private SearchView runMergedSearch(SearchQuery query, List<UUID> sharedFileIds, List<UUID> sharedFolderIds) {
        int fetch = Math.max(1, query.page()) * Math.max(1, query.size());

        NativeQuery fileNq = buildFileQuery(query, sharedFileIds, 1, fetch, true);
        NativeQuery folderNq = buildFolderQuery(query, sharedFolderIds, 1, fetch);

        // Parallel execution — each query is bounded so this is safe.
        CompletableFuture<SearchHits<FileDocument>> filesFuture =
                CompletableFuture.supplyAsync(() ->
                        elasticsearchOperations.search(fileNq, FileDocument.class, filesIndex()));
        CompletableFuture<SearchHits<FolderDocument>> foldersFuture =
                CompletableFuture.supplyAsync(() ->
                        elasticsearchOperations.search(folderNq, FolderDocument.class, foldersIndex()));

        SearchHits<FileDocument> fileHits = filesFuture.join();
        SearchHits<FolderDocument> folderHits = foldersFuture.join();

        List<SearchHitView> merged = new ArrayList<>(fileHits.getSearchHits().size() + folderHits.getSearchHits().size());
        merged.addAll(mapFileHits(fileHits));
        merged.addAll(mapFolderHits(folderHits));
        merged.sort(Comparator.comparingDouble(SearchHitView::score).reversed());

        long totalHits = fileHits.getTotalHits() + folderHits.getTotalHits();
        int from = (Math.max(1, query.page()) - 1) * Math.max(1, query.size());
        int to = Math.min(from + Math.max(1, query.size()), merged.size());
        List<SearchHitView> page = (from >= merged.size()) ? Collections.emptyList() : merged.subList(from, to);

        return new SearchView(page, totalHits, query.page(), query.size(),
                extractMimeAggregation(fileHits));
    }

    // ── Read operations: SAYT ────────────────────────────────────────────────

    @Override
    public SuggestView suggest(SuggestQuery query, Collection<UUID> sharedFileIds, Collection<UUID> sharedFolderIds) {
        SearchType type = query.type() == null ? SearchType.ALL : query.type();
        int size = Math.max(1, query.size());

        return switch (type) {
            case FILE -> runFileSuggest(query, sharedFileIds, size);
            case FOLDER -> runFolderSuggest(query, sharedFolderIds, size);
            case ALL -> runMergedSuggest(query, sharedFileIds, sharedFolderIds, size);
        };
    }

    private SuggestView runFileSuggest(SuggestQuery query, Collection<UUID> sharedFileIds, int size) {
        NativeQuery nq = buildFileSuggestQuery(query, sharedFileIds, size);
        SearchHits<FileDocument> hits = elasticsearchOperations.search(nq, FileDocument.class, filesIndex());
        List<SuggestHitView> items = mapFileSuggestHits(hits);
        return new SuggestView(items, hits.getTotalHits());
    }

    private SuggestView runFolderSuggest(SuggestQuery query, Collection<UUID> sharedFolderIds, int size) {
        NativeQuery nq = buildFolderSuggestQuery(query, sharedFolderIds, size);
        SearchHits<FolderDocument> hits = elasticsearchOperations.search(nq, FolderDocument.class, foldersIndex());
        List<SuggestHitView> items = mapFolderSuggestHits(hits);
        return new SuggestView(items, hits.getTotalHits());
    }

    private SuggestView runMergedSuggest(SuggestQuery query,
                                         Collection<UUID> sharedFileIds,
                                         Collection<UUID> sharedFolderIds,
                                         int size) {
        NativeQuery fileNq = buildFileSuggestQuery(query, sharedFileIds, size);
        NativeQuery folderNq = buildFolderSuggestQuery(query, sharedFolderIds, size);

        CompletableFuture<SearchHits<FileDocument>> filesFuture =
                CompletableFuture.supplyAsync(() ->
                        elasticsearchOperations.search(fileNq, FileDocument.class, filesIndex()));
        CompletableFuture<SearchHits<FolderDocument>> foldersFuture =
                CompletableFuture.supplyAsync(() ->
                        elasticsearchOperations.search(folderNq, FolderDocument.class, foldersIndex()));

        SearchHits<FileDocument> fileHits = filesFuture.join();
        SearchHits<FolderDocument> folderHits = foldersFuture.join();

        // Sort across both indexes by ES _score DESC before trimming. Concat-then-trim
        // alone would always exhaust file hits first and starve folders out of the
        // dropdown when there were enough file matches to fill `size`.
        List<ScoredSuggest> scored = new ArrayList<>(
                fileHits.getSearchHits().size() + folderHits.getSearchHits().size());
        for (SearchHit<FileDocument> h : fileHits.getSearchHits()) {
            scored.add(new ScoredSuggest(h.getScore(), toSuggestHit(h, SearchType.FILE)));
        }
        for (SearchHit<FolderDocument> h : folderHits.getSearchHits()) {
            scored.add(new ScoredSuggest(h.getScore(), toSuggestHit(h, SearchType.FOLDER)));
        }
        scored.sort(Comparator.comparingDouble(ScoredSuggest::score).reversed());

        List<SuggestHitView> merged = new ArrayList<>(Math.min(size, scored.size()));
        for (int i = 0; i < scored.size() && i < size; i++) {
            merged.add(scored.get(i).hit());
        }
        return new SuggestView(merged, fileHits.getTotalHits() + folderHits.getTotalHits());
    }

    private record ScoredSuggest(double score, SuggestHitView hit) {}

    // ── Query builders ───────────────────────────────────────────────────────

    private NativeQuery buildFileQuery(SearchQuery query, List<UUID> sharedFileIds, int page, int size, boolean withAggregations) {
        // Inner bool: name (precision) ∪ content (recall), minimum_should_match = 1
        Query nameClause = Query.of(q -> q.multiMatch(MultiMatchQuery.of(m -> m
                .query(query.q())
                .fields("originalName^5")
                .fuzziness("AUTO")
                .operator(Operator.And)
        )));
        Query contentClause = Query.of(q -> q.multiMatch(MultiMatchQuery.of(m -> m
                .query(query.q())
                .fields("content^1")
                .fuzziness("AUTO")
                .operator(Operator.Or)
                .minimumShouldMatch("75%")
        )));
        Query relevance = Query.of(q -> q.bool(BoolQuery.of(b -> b
                .should(nameClause)
                .should(contentClause)
                .minimumShouldMatch("1")
        )));

        BoolQuery.Builder root = new BoolQuery.Builder().must(relevance);
        applyVisibilityFilter(root, query.userId(), query.tenantId(), query.role(), "uploadedBy", sharedFileIds);
        applyMimeFilter(root, query.mimeTypes());
        applyDateRange(root, query.dateFrom(), query.dateTo());

        NativeQueryBuilder nqb = NativeQuery.builder()
                .withQuery(q -> q.bool(root.build()))
                .withPageable(org.springframework.data.domain.PageRequest.of(Math.max(0, page - 1), Math.max(1, size)))
                .withSourceFilter(new FetchSourceFilter(true, new String[0], new String[]{"content"}))
                .withHighlightQuery(buildHighlight());

        applySort(nqb, query.sort(), query.sortDirection(), "originalName");

        if (withAggregations) {
            Aggregation mimeAgg = Aggregation.of(a -> a.terms(t -> t.field("mimeType").size(20)));
            nqb.withAggregation("mimeTypeCounts", mimeAgg);
        }
        return nqb.build();
    }

    private NativeQuery buildFolderQuery(SearchQuery query, List<UUID> sharedFolderIds, int page, int size) {
        Query nameMatch = Query.of(q -> q.multiMatch(MultiMatchQuery.of(m -> m
                .query(query.q())
                .fields("name^5")
                .fuzziness("AUTO")
                .operator(Operator.And)
        )));

        BoolQuery.Builder root = new BoolQuery.Builder().must(nameMatch);
        applyVisibilityFilter(root, query.userId(), query.tenantId(), query.role(), "createdBy", sharedFolderIds);
        // Folder docs have no mimeType — mimeType filter is a no-op here.
        applyDateRange(root, query.dateFrom(), query.dateTo());

        NativeQueryBuilder nqb = NativeQuery.builder()
                .withQuery(q -> q.bool(root.build()))
                .withPageable(org.springframework.data.domain.PageRequest.of(Math.max(0, page - 1), Math.max(1, size)));

        applySort(nqb, query.sort(), query.sortDirection(), "name");
        return nqb.build();
    }

    private NativeQuery buildFileSuggestQuery(SuggestQuery query, Collection<UUID> sharedFileIds, int size) {
        Query bp = Query.of(q -> q.multiMatch(MultiMatchQuery.of(m -> m
                .query(query.q())
                .type(TextQueryType.BoolPrefix)
                .fields("originalName.sayt", "originalName.sayt._2gram", "originalName.sayt._3gram")
        )));
        BoolQuery.Builder root = new BoolQuery.Builder().must(bp);
        applyVisibilityFilter(root, query.userId(), query.tenantId(), query.role(), "uploadedBy", sharedFileIds);

        return NativeQuery.builder()
                .withQuery(q -> q.bool(root.build()))
                .withPageable(org.springframework.data.domain.PageRequest.of(0, size))
                .withSourceFilter(new FetchSourceFilter(
                        true, new String[]{"id", "tenantId", "folderId", "mimeType", "originalName"}, new String[0]))
                .build();
    }

    private NativeQuery buildFolderSuggestQuery(SuggestQuery query, Collection<UUID> sharedFolderIds, int size) {
        Query bp = Query.of(q -> q.multiMatch(MultiMatchQuery.of(m -> m
                .query(query.q())
                .type(TextQueryType.BoolPrefix)
                .fields("name.sayt", "name.sayt._2gram", "name.sayt._3gram")
        )));
        BoolQuery.Builder root = new BoolQuery.Builder().must(bp);
        applyVisibilityFilter(root, query.userId(), query.tenantId(), query.role(), "createdBy", sharedFolderIds);

        return NativeQuery.builder()
                .withQuery(q -> q.bool(root.build()))
                .withPageable(org.springframework.data.domain.PageRequest.of(0, size))
                .withSourceFilter(new FetchSourceFilter(true, new String[]{"id", "tenantId", "parentId", "name"}, new String[0]))
                .build();
    }

    private static HighlightQuery buildHighlight() {
        HighlightFieldParameters nameParams = HighlightFieldParameters.builder()
                .withNumberOfFragments(1)
                .withFragmentSize(150)
                .build();
        HighlightFieldParameters contentParams = HighlightFieldParameters.builder()
                .withNumberOfFragments(3)
                .withFragmentSize(200)
                .withFragmenter("span")
                .build();
        Highlight highlight = new Highlight(List.of(
                new HighlightField("originalName", nameParams),
                new HighlightField("content", contentParams)
        ));
        return new HighlightQuery(highlight, null);
    }

    // ── Filters ──────────────────────────────────────────────────────────────

    /**
     * MANDATORY on every search/suggest query. Applies tenant + role + share
     * filters in one place. Bypassing this helper from any new query method is
     * a review-blocker.
     *
     * <p>Rules:
     * <ul>
     *   <li>{@code SUPER_ADMIN}: no filter at all.</li>
     *   <li>{@code TENANT_ADMIN}: {@code tenantId == X}.</li>
     *   <li>{@code MEMBER}: {@code tenantId == X} AND
     *       ({@code ownerField == userId} OR {@code id IN sharedIds}).</li>
     * </ul>
     *
     * @param ownerField {@code uploadedBy} for files, {@code createdBy} for folders.
     * @param sharedIds  pre-fetched share ids for the current user; ignored for
     *                   non-MEMBER roles.
     */
    private static void applyVisibilityFilter(BoolQuery.Builder bool,
                                              UUID userId,
                                              UUID tenantId,
                                              RoleCode role,
                                              String ownerField,
                                              Collection<UUID> sharedIds) {
        if (role == RoleCode.SUPER_ADMIN) {
            return;
        }
        bool.filter(Query.of(q -> q.term(TermQuery.of(t -> t
                .field("tenantId").value(tenantId.toString())))));

        if (role == RoleCode.MEMBER) {
            Query ownerClause = Query.of(q -> q.term(TermQuery.of(t -> t
                    .field(ownerField).value(userId.toString()))));

            BoolQuery.Builder access = new BoolQuery.Builder().should(ownerClause).minimumShouldMatch("1");
            if (sharedIds != null && !sharedIds.isEmpty()) {
                List<String> idStrings = sharedIds.stream().map(UUID::toString).toList();
                Query idsClause = Query.of(q -> q.ids(IdsQuery.of(i -> i.values(idStrings))));
                access.should(idsClause);
            }
            bool.filter(Query.of(q -> q.bool(access.build())));
        }
    }

    private static void applyMimeFilter(BoolQuery.Builder bool, List<String> mimeTypes) {
        if (mimeTypes == null || mimeTypes.isEmpty()) {
            return;
        }
        List<FieldValue> values = mimeTypes.stream().map(FieldValue::of).toList();
        bool.filter(Query.of(q -> q.terms(TermsQuery.of(t -> t
                .field("mimeType")
                .terms(TermsQueryField.of(tf -> tf.value(values)))))));
    }

    private static void applyDateRange(BoolQuery.Builder bool, Instant from, Instant to) {
        if (from == null && to == null) {
            return;
        }
        bool.filter(Query.of(q -> q.range(r -> r.date(d -> {
            d.field("createdAt");
            if (from != null) d.gte(from.toString());
            if (to != null) d.lte(to.toString());
            return d;
        }))));
    }

    private static void applySort(NativeQueryBuilder nqb, SearchSortBy sortBy, SortDirection direction, String nameField) {
        if (sortBy == null || sortBy == SearchSortBy.SCORE) {
            return; // default: _score DESC
        }
        SortOrder order = direction == SortDirection.ASC ? SortOrder.Asc : SortOrder.Desc;
        String field = switch (sortBy) {
            case NAME -> nameField + ".keyword";
            case CREATED_AT -> "createdAt";
            default -> null;
        };
        if (field == null) return;
        String finalField = field;
        nqb.withSort(SortOptions.of(s -> s.field(f -> f.field(finalField).order(order))));
    }

    // ── Hit → view mapping ───────────────────────────────────────────────────

    private static List<SearchHitView> mapFileHits(SearchHits<FileDocument> hits) {
        List<SearchHitView> result = new ArrayList<>(hits.getSearchHits().size());
        for (SearchHit<FileDocument> h : hits.getSearchHits()) {
            FileDocument doc = h.getContent();
            result.add(new SearchHitView(
                    UUID.fromString(doc.getId()),
                    SearchType.FILE,
                    UUID.fromString(doc.getTenantId()),
                    doc.getOriginalName(),
                    doc.getMimeType(),
                    doc.getFolderId() == null ? null : UUID.fromString(doc.getFolderId()),
                    null,
                    doc.getCreatedAt(),
                    h.getScore(),
                    h.getHighlightFields()
            ));
        }
        return result;
    }

    private static List<SearchHitView> mapFolderHits(SearchHits<FolderDocument> hits) {
        List<SearchHitView> result = new ArrayList<>(hits.getSearchHits().size());
        for (SearchHit<FolderDocument> h : hits.getSearchHits()) {
            FolderDocument doc = h.getContent();
            result.add(new SearchHitView(
                    UUID.fromString(doc.getId()),
                    SearchType.FOLDER,
                    UUID.fromString(doc.getTenantId()),
                    doc.getName(),
                    null,
                    doc.getParentId() == null ? null : UUID.fromString(doc.getParentId()),
                    null,
                    doc.getCreatedAt(),
                    h.getScore(),
                    h.getHighlightFields()
            ));
        }
        return result;
    }

    private static List<SuggestHitView> mapFileSuggestHits(SearchHits<FileDocument> hits) {
        List<SuggestHitView> result = new ArrayList<>(hits.getSearchHits().size());
        for (SearchHit<FileDocument> h : hits.getSearchHits()) {
            FileDocument doc = h.getContent();
            result.add(new SuggestHitView(
                    UUID.fromString(doc.getId()),
                    SearchType.FILE,
                    doc.getOriginalName(),
                    doc.getMimeType(),
                    doc.getFolderId() == null ? null : UUID.fromString(doc.getFolderId()),
                    null
            ));
        }
        return result;
    }

    private static List<SuggestHitView> mapFolderSuggestHits(SearchHits<FolderDocument> hits) {
        List<SuggestHitView> result = new ArrayList<>(hits.getSearchHits().size());
        for (SearchHit<FolderDocument> h : hits.getSearchHits()) {
            FolderDocument doc = h.getContent();
            result.add(new SuggestHitView(
                    UUID.fromString(doc.getId()),
                    SearchType.FOLDER,
                    doc.getName(),
                    null,
                    doc.getParentId() == null ? null : UUID.fromString(doc.getParentId()),
                    null
            ));
        }
        return result;
    }

    private static SuggestHitView toSuggestHit(SearchHit<?> h, SearchType type) {
        if (type == SearchType.FILE) {
            FileDocument doc = (FileDocument) h.getContent();
            return new SuggestHitView(
                    UUID.fromString(doc.getId()),
                    SearchType.FILE,
                    doc.getOriginalName(),
                    doc.getMimeType(),
                    doc.getFolderId() == null ? null : UUID.fromString(doc.getFolderId()),
                    null
            );
        }
        FolderDocument doc = (FolderDocument) h.getContent();
        return new SuggestHitView(
                UUID.fromString(doc.getId()),
                SearchType.FOLDER,
                doc.getName(),
                null,
                doc.getParentId() == null ? null : UUID.fromString(doc.getParentId()),
                null
        );
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    private static Map<String, Map<String, Long>> extractMimeAggregation(SearchHits<?> hits) {
        Map<String, Map<String, Long>> result = new HashMap<>();
        try {
            var aggsContainer = hits.getAggregations();
            if (aggsContainer == null) {
                return Collections.emptyMap();
            }
            var raw = aggsContainer.aggregations();
            if (raw == null) {
                return Collections.emptyMap();
            }
            // The Spring Data ES wrapper exposes aggregations differently across versions.
            // For robustness we try Elastic's native StringTermsAggregate via the wrapper.
            Object mimeAgg = raw.getClass().getMethod("get", Object.class).invoke(raw, "mimeTypeCounts");
            if (mimeAgg == null) {
                return Collections.emptyMap();
            }
            Map<String, Long> bucket = new HashMap<>();
            var aggregateMethod = mimeAgg.getClass().getMethod("aggregation");
            Object aggregate = aggregateMethod.invoke(mimeAgg);
            // Aggregate#sterms() returns StringTermsAggregate when buckets are keyword-based.
            var stermsMethod = aggregate.getClass().getMethod("sterms");
            Object sterms = stermsMethod.invoke(aggregate);
            var bucketsMethod = sterms.getClass().getMethod("buckets");
            Object buckets = bucketsMethod.invoke(sterms);
            var arrayMethod = buckets.getClass().getMethod("array");
            List<?> list = (List<?>) arrayMethod.invoke(buckets);
            for (Object b : list) {
                String key = (String) b.getClass().getMethod("key").invoke(b);
                long docCount = (long) b.getClass().getMethod("docCount").invoke(b);
                bucket.put(key, docCount);
            }
            result.put("mimeTypeCounts", bucket);
        } catch (Exception e) {
            log.debug("mimeTypeCounts aggregation could not be extracted: {}", e.getMessage());
        }
        return result;
    }
}
