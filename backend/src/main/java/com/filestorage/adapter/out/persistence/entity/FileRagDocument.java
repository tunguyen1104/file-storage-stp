package com.filestorage.adapter.out.persistence.entity;

import com.filestorage.domain.model.enums.RagDocumentStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "file_rag_documents",
        indexes = {
                @Index(name = "idx_file_rag_documents_status", columnList = "status")
        }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FileRagDocument {

    @Id
    @Column(name = "file_id", nullable = false, updatable = false, columnDefinition = "uuid")
    private UUID fileId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private RagDocumentStatus status = RagDocumentStatus.PENDING;

    @Column(name = "source_key", length = 1000)
    private String sourceKey;

    @Column(name = "source_type", length = 50, nullable = false)
    @Builder.Default
    private String sourceType = "EXTRACTED_TEXT";

    @Column(name = "chunk_count", nullable = false)
    @Builder.Default
    private int chunkCount = 0;

    @Column(name = "embedding_model", length = 200)
    private String embeddingModel;

    @Column(name = "embedding_dimensions")
    private Integer embeddingDimensions;

    @Column(name = "chunker_version", length = 50)
    private String chunkerVersion;

    @Column(name = "active_ingestion_id", columnDefinition = "uuid")
    private UUID activeIngestionId;

    @Column(name = "retry_count", nullable = false)
    @Builder.Default
    private int retryCount = 0;

    @Column(name = "last_error", columnDefinition = "text")
    private String lastError;

    @Column(name = "indexed_at")
    private Instant indexedAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;
}
