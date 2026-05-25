package com.filestorage.adapter.out.search;

import co.elastic.clients.elasticsearch._types.FieldValue;
import co.elastic.clients.elasticsearch._types.KnnSearch;
import co.elastic.clients.elasticsearch._types.query_dsl.BoolQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.MultiMatchQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.Operator;
import co.elastic.clients.elasticsearch._types.query_dsl.Query;
import co.elastic.clients.elasticsearch._types.query_dsl.TermQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TermsQuery;
import co.elastic.clients.elasticsearch._types.query_dsl.TermsQueryField;
import com.filestorage.adapter.out.search.document.RagChunkDocument;
import com.filestorage.domain.model.enums.FileStatus;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.rag.RagAccessScope;
import com.filestorage.domain.model.rag.RagChunkIndexData;
import com.filestorage.domain.model.rag.RagRetrievedChunk;
import com.filestorage.domain.port.out.RagVectorIndexPort;
import com.filestorage.infrastructure.config.ElasticsearchProperties;
import com.filestorage.infrastructure.config.RagProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.elasticsearch.client.elc.NativeQuery;
import org.springframework.data.elasticsearch.core.ElasticsearchOperations;
import org.springframework.data.elasticsearch.core.RefreshPolicy;
import org.springframework.data.elasticsearch.core.SearchHit;
import org.springframework.data.elasticsearch.core.SearchHits;
import org.springframework.data.elasticsearch.core.mapping.IndexCoordinates;
import org.springframework.data.elasticsearch.core.query.DeleteQuery;
import org.springframework.data.elasticsearch.core.query.FetchSourceFilter;
import org.springframework.data.elasticsearch.core.query.IndexQuery;
import org.springframework.data.elasticsearch.core.query.IndexQueryBuilder;
import org.springframework.data.elasticsearch.core.query.UpdateQuery;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Component
@Slf4j
@RequiredArgsConstructor
public class ElasticsearchRagVectorAdapter implements RagVectorIndexPort {

    private static final int UPDATE_BY_QUERY_BATCH_SIZE = 1000;
    private static final int RRF_K = 60;

    private final ElasticsearchOperations elasticsearchOperations;
    private final ElasticsearchProperties properties;
    private final RagProperties ragProperties;

    @Override
    public void upsertChunks(List<RagChunkIndexData> chunks) {
        if (chunks == null || chunks.isEmpty()) {
            return;
        }

        List<IndexQuery> queries = chunks.stream()
                .map(this::toIndexQuery)
                .toList();
        elasticsearchOperations.bulkIndex(queries, ragChunksIndex());
        elasticsearchOperations.indexOps(ragChunksIndex()).refresh();
        log.info("Indexed {} RAG chunk docs for fileId={} ingestionId={}",
                chunks.size(), chunks.get(0).fileId(), chunks.get(0).ingestionId());
    }

    @Override
    public void markChunksReadyByFileIdAndIngestionId(UUID fileId, UUID ingestionId) {
        updateByQuery(
                fileIdAndIngestionQuery(fileId, ingestionId),
                "ctx._source.ready = true",
                Map.of()
        );
        log.info("Marked RAG chunks ready fileId={} ingestionId={}", fileId, ingestionId);
    }

    @Override
    public void deleteChunksByFileId(UUID fileId) {
        deleteByQuery(fileIdQuery(fileId));
        log.info("Deleted RAG chunks fileId={}", fileId);
    }

    @Override
    public void deleteChunksByFileIdAndIngestionId(UUID fileId, UUID ingestionId) {
        deleteByQuery(fileIdAndIngestionQuery(fileId, ingestionId));
        log.info("Deleted RAG chunks fileId={} ingestionId={}", fileId, ingestionId);
    }

    @Override
    public void deleteChunksByFileIdExceptIngestionId(UUID fileId, UUID ingestionId) {
        deleteByQuery(fileIdExceptIngestionQuery(fileId, ingestionId));
        log.info("Deleted stale RAG chunks fileId={} keepIngestionId={}", fileId, ingestionId);
    }

    @Override
    public void updateFileMetadata(UUID fileId, String originalName, UUID folderId) {
        Map<String, Object> params = new HashMap<>();
        params.put("originalName", originalName);
        String script = "ctx._source.originalName = params.originalName; ";
        if (folderId == null) {
            script += "ctx._source.remove('folderId');";
        } else {
            params.put("folderId", folderId.toString());
            script += "ctx._source.folderId = params.folderId;";
        }
        updateByQuery(
                fileIdQuery(fileId),
                script,
                params
        );
        log.info("Updated RAG chunk metadata fileId={} fields=[originalName, folderId]", fileId);
    }

    @Override
    public void updateFileRetrievability(UUID fileId, boolean retrievable, FileStatus fileStatus) {
        Map<String, Object> params = Map.of(
                "retrievable", retrievable,
                "fileStatus", fileStatus.name()
        );
        updateByQuery(
                fileIdQuery(fileId),
                "ctx._source.retrievable = params.retrievable; ctx._source.fileStatus = params.fileStatus",
                params
        );
        log.info("Updated RAG chunk retrievability fileId={} retrievable={} fileStatus={}",
                fileId, retrievable, fileStatus);
    }

    @Override
    public boolean hasReadyChunksByFileId(UUID fileId) {
        Query query = Query.of(q -> q.bool(BoolQuery.of(b -> b
                .filter(fileIdQuery(fileId))
                .filter(termQuery("ready", true))
        )));
        return elasticsearchOperations.count(nativeQuery(query), RagChunkDocument.class, ragChunksIndex()) > 0;
    }

    @Override
    public List<RagRetrievedChunk> hybridSearch(String queryText, List<Float> queryVector, RagAccessScope accessScope, int topK) {
        if (queryText == null || queryText.isBlank() || queryVector == null || queryVector.isEmpty() || topK < 1) {
            return List.of();
        }

        List<RagRetrievedChunk> vectorHits = searchVector(queryVector, accessScope,
                Math.max(topK, ragProperties.getRetrieval().getVectorCandidates()));
        List<RagRetrievedChunk> bm25Hits = searchBm25(queryText, accessScope,
                Math.max(topK, ragProperties.getRetrieval().getBm25Candidates()));
        return mergeRrf(vectorHits, bm25Hits, topK);
    }

    private IndexCoordinates ragChunksIndex() {
        return IndexCoordinates.of(properties.getIndices().getRagChunks());
    }

    private List<RagRetrievedChunk> searchVector(List<Float> queryVector, RagAccessScope accessScope, int size) {
        Query filter = accessFilter(accessScope);
        NativeQuery query = NativeQuery.builder()
                .withKnnSearches(KnnSearch.of(k -> k
                        .field("contentVector")
                        .queryVector(queryVector)
                        .k(size)
                        .numCandidates(Math.max(size, ragProperties.getRetrieval().getVectorCandidates()))
                        .filter(filter)
                ))
                .withPageable(PageRequest.of(0, size))
                .withSourceFilter(new FetchSourceFilter(true, new String[0], new String[]{"contentVector"}))
                .build();

        SearchHits<RagChunkDocument> hits = elasticsearchOperations.search(query, RagChunkDocument.class, ragChunksIndex());
        return mapHits(hits, true);
    }

    private List<RagRetrievedChunk> searchBm25(String queryText, RagAccessScope accessScope, int size) {
        Query textQuery = Query.of(q -> q.multiMatch(MultiMatchQuery.of(m -> m
                .query(queryText)
                .fields("content^3", "originalName^1")
                .operator(Operator.Or)
        )));
        Query query = Query.of(q -> q.bool(BoolQuery.of(b -> b
                .must(textQuery)
                .filter(accessFilter(accessScope))
        )));

        NativeQuery nativeQuery = NativeQuery.builder()
                .withQuery(query)
                .withPageable(PageRequest.of(0, size))
                .withSourceFilter(new FetchSourceFilter(true, new String[0], new String[]{"contentVector"}))
                .build();

        SearchHits<RagChunkDocument> hits = elasticsearchOperations.search(nativeQuery, RagChunkDocument.class, ragChunksIndex());
        return mapHits(hits, false);
    }

    private static Query accessFilter(RagAccessScope accessScope) {
        BoolQuery.Builder root = new BoolQuery.Builder()
                .filter(termQuery("ready", true))
                .filter(termQuery("retrievable", true))
                .filter(termQuery("fileStatus", FileStatus.ACTIVE.name()))
                .filter(termQuery("tenantId", accessScope.tenantId().toString()));

        if (accessScope.role() == RoleCode.MEMBER) {
            BoolQuery.Builder access = new BoolQuery.Builder()
                    .should(termQuery("uploadedBy", accessScope.userId().toString()))
                    .minimumShouldMatch("1");
            addTermsShould(access, "fileId", accessScope.sharedFileIds());
            addTermsShould(access, "folderId", accessScope.sharedFolderIds());
            root.filter(Query.of(q -> q.bool(access.build())));
        }
        return Query.of(q -> q.bool(root.build()));
    }

    private static void addTermsShould(BoolQuery.Builder bool, String field, Collection<UUID> values) {
        if (values == null || values.isEmpty()) {
            return;
        }
        List<FieldValue> fieldValues = values.stream()
                .map(id -> FieldValue.of(id.toString()))
                .toList();
        bool.should(Query.of(q -> q.terms(TermsQuery.of(t -> t
                .field(field)
                .terms(TermsQueryField.of(tf -> tf.value(fieldValues)))
        ))));
    }

    private static List<RagRetrievedChunk> mapHits(SearchHits<RagChunkDocument> hits, boolean vector) {
        List<RagRetrievedChunk> result = new ArrayList<>(hits.getSearchHits().size());
        for (SearchHit<RagChunkDocument> hit : hits.getSearchHits()) {
            RagChunkDocument doc = hit.getContent();
            double score = hit.getScore();
            result.add(new RagRetrievedChunk(
                    doc.getId(),
                    UUID.fromString(doc.getFileId()),
                    UUID.fromString(doc.getTenantId()),
                    doc.getFolderId() == null ? null : UUID.fromString(doc.getFolderId()),
                    UUID.fromString(doc.getUploadedBy()),
                    doc.getOriginalName(),
                    doc.getChunkIndex(),
                    doc.getContent(),
                    doc.getSourceKey(),
                    score,
                    vector ? score : 0.0,
                    vector ? 0.0 : score
            ));
        }
        return result;
    }

    private static List<RagRetrievedChunk> mergeRrf(
            List<RagRetrievedChunk> vectorHits,
            List<RagRetrievedChunk> bm25Hits,
            int topK
    ) {
        Map<String, RrfCandidate> candidates = new LinkedHashMap<>();
        addRanked(candidates, vectorHits, true);
        addRanked(candidates, bm25Hits, false);
        return candidates.values().stream()
                .sorted(Comparator.comparingDouble(RrfCandidate::score).reversed())
                .limit(topK)
                .map(RrfCandidate::toChunk)
                .toList();
    }

    private static void addRanked(Map<String, RrfCandidate> candidates, List<RagRetrievedChunk> hits, boolean vector) {
        for (int i = 0; i < hits.size(); i++) {
            RagRetrievedChunk hit = hits.get(i);
            RrfCandidate candidate = candidates.computeIfAbsent(hit.id(), id -> new RrfCandidate(hit));
            double rrf = 1.0 / (RRF_K + i + 1);
            if (vector) {
                candidate.vectorScore = hit.vectorScore();
                candidate.rrfScore += rrf;
            } else {
                candidate.bm25Score = hit.bm25Score();
                candidate.rrfScore += rrf;
            }
        }
    }

    private static class RrfCandidate {
        private final RagRetrievedChunk chunk;
        private double rrfScore;
        private double vectorScore;
        private double bm25Score;

        private RrfCandidate(RagRetrievedChunk chunk) {
            this.chunk = chunk;
            this.vectorScore = chunk.vectorScore();
            this.bm25Score = chunk.bm25Score();
        }

        private double score() {
            return rrfScore;
        }

        private RagRetrievedChunk toChunk() {
            return new RagRetrievedChunk(
                    chunk.id(),
                    chunk.fileId(),
                    chunk.tenantId(),
                    chunk.folderId(),
                    chunk.uploadedBy(),
                    chunk.originalName(),
                    chunk.chunkIndex(),
                    chunk.content(),
                    chunk.sourceKey(),
                    rrfScore,
                    vectorScore,
                    bm25Score
            );
        }
    }

    private IndexQuery toIndexQuery(RagChunkIndexData data) {
        RagChunkDocument document = RagChunkDocument.builder()
                .id(RagChunkDocument.idOf(data.fileId(), data.ingestionId(), data.chunkIndex()))
                .fileId(data.fileId().toString())
                .tenantId(data.tenantId().toString())
                .folderId(data.folderId() == null ? null : data.folderId().toString())
                .uploadedBy(data.uploadedBy().toString())
                .originalName(data.originalName())
                .chunkIndex(data.chunkIndex())
                .ingestionId(data.ingestionId().toString())
                .content(data.content())
                .contentVector(data.contentVector())
                .sourceKey(data.sourceKey())
                .embeddingModel(data.embeddingModel())
                .chunkerVersion(data.chunkerVersion())
                .ready(data.ready())
                .retrievable(data.retrievable())
                .fileStatus(data.fileStatus().name())
                .createdAt(data.createdAt() == null ? Instant.now() : data.createdAt())
                .build();
        return new IndexQueryBuilder()
                .withId(document.getId())
                .withObject(document)
                .build();
    }

    private void deleteByQuery(Query query) {
        DeleteQuery deleteQuery = DeleteQuery.builder(nativeQuery(query))
                .withRefresh(true)
                .build();
        elasticsearchOperations.delete(deleteQuery, RagChunkDocument.class, ragChunksIndex());
    }

    private void updateByQuery(Query query, String script, Map<String, Object> params) {
        UpdateQuery updateQuery = UpdateQuery.builder(nativeQuery(query))
                .withScript(script)
                .withLang("painless")
                .withParams(params)
                .withAbortOnVersionConflict(false)
                .withBatchSize(UPDATE_BY_QUERY_BATCH_SIZE)
                .withRefreshPolicy(RefreshPolicy.IMMEDIATE)
                .build();
        elasticsearchOperations.updateByQuery(updateQuery, ragChunksIndex());
    }

    private static org.springframework.data.elasticsearch.core.query.Query nativeQuery(Query query) {
        return NativeQuery.builder()
                .withQuery(query)
                .build();
    }

    private static Query fileIdQuery(UUID fileId) {
        return termQuery("fileId", fileId.toString());
    }

    private static Query fileIdAndIngestionQuery(UUID fileId, UUID ingestionId) {
        return Query.of(q -> q.bool(BoolQuery.of(b -> b
                .filter(fileIdQuery(fileId))
                .filter(termQuery("ingestionId", ingestionId.toString()))
        )));
    }

    private static Query fileIdExceptIngestionQuery(UUID fileId, UUID ingestionId) {
        return Query.of(q -> q.bool(BoolQuery.of(b -> b
                .filter(fileIdQuery(fileId))
                .mustNot(termQuery("ingestionId", ingestionId.toString()))
        )));
    }

    private static Query termQuery(String field, String value) {
        return Query.of(q -> q.term(TermQuery.of(t -> t
                .field(field)
                .value(value)
        )));
    }

    private static Query termQuery(String field, boolean value) {
        return Query.of(q -> q.term(TermQuery.of(t -> t
                .field(field)
                .value(value)
        )));
    }
}
