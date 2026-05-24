package com.filestorage.domain.port.out;

import com.filestorage.domain.model.enums.FileStatus;
import com.filestorage.domain.model.rag.RagAccessScope;
import com.filestorage.domain.model.rag.RagChunkIndexData;
import com.filestorage.domain.model.rag.RagRetrievedChunk;

import java.util.List;
import java.util.UUID;

public interface RagVectorIndexPort {

    void upsertChunks(List<RagChunkIndexData> chunks);

    void markChunksReadyByFileIdAndIngestionId(UUID fileId, UUID ingestionId);

    void deleteChunksByFileId(UUID fileId);

    void deleteChunksByFileIdAndIngestionId(UUID fileId, UUID ingestionId);

    void deleteChunksByFileIdExceptIngestionId(UUID fileId, UUID ingestionId);

    void updateFileMetadata(UUID fileId, String originalName, UUID folderId);

    void updateFileRetrievability(UUID fileId, boolean retrievable, FileStatus fileStatus);

    boolean hasReadyChunksByFileId(UUID fileId);

    List<RagRetrievedChunk> hybridSearch(String queryText, List<Float> queryVector, RagAccessScope accessScope, int topK);
}
