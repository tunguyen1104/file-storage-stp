package com.filestorage.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(
        name = "rag_chat_message_sources",
        indexes = {
                @Index(name = "idx_rag_chat_message_sources_message", columnList = "message_id"),
                @Index(name = "idx_rag_chat_message_sources_file", columnList = "file_id")
        }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class RagChatMessageSource {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false, columnDefinition = "uuid")
    private UUID id;

    @Column(name = "message_id", nullable = false, columnDefinition = "uuid")
    private UUID messageId;

    @Column(name = "file_id", nullable = false, columnDefinition = "uuid")
    private UUID fileId;

    @Column(name = "original_name", length = 500, nullable = false)
    private String originalName;

    @Column(name = "chunk_index", nullable = false)
    private int chunkIndex;

    @Column(name = "snippet", columnDefinition = "text")
    private String snippet;

    @Column(name = "score")
    private Double score;

    @Column(name = "source_key", length = 1000)
    private String sourceKey;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;
}
