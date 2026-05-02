package com.filestorage.adapter.out.persistence.entity;

import com.filestorage.domain.model.enums.MultipartUploadStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import jakarta.persistence.EntityListeners;

import java.time.Instant;
import java.util.List;

/**
 * Tracks in-progress chunked (multipart) uploads for files larger than 100 MB.
 *
 * <p>Redis caches the session for fast part-completion lookups (24 h TTL);
 * this table is the source of truth and is used for crash recovery and cleanup.
 *
 * <p>Lifecycle:
 * <ul>
 *   <li>{@code IN_PROGRESS} — parts are being uploaded to MinIO.</li>
 *   <li>{@code COMPLETED} — all parts received; {@code files.status → ACTIVE}.</li>
 *   <li>{@code ABORTED} — client cancelled or background job expired the session;
 *       MinIO multipart upload cancelled, {@code files.status → UPLOAD_FAILED}.</li>
 * </ul>
 */
@Entity
@Table(
        name = "multipart_upload_sessions",
        indexes = {
                @Index(
                        name = "idx_multipart_expires_at",
                        columnList = "expires_at"
                )
        }
)
@EntityListeners(AuditingEntityListener.class)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MultipartUploadSession {

    @Id
    @Column(name = "upload_id", length = 255, nullable = false, updatable = false)
    private String uploadId;

    /**
     * Pre-assigned {@link File} record created before upload completes.
     */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "file_id", nullable = false, updatable = false)
    private File file;


    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "initiated_by", nullable = false, updatable = false)
    private User initiatedBy;

    /** MinIO object key for this multipart upload. */
    @Column(name = "object_key", length = 1000, nullable = false, updatable = false)
    private String objectKey;

    @Column(name = "original_name", length = 500, nullable = false, updatable = false)
    private String originalName;

    @Column(name = "mime_type", length = 100)
    private String mimeType;

    /** Declared total file size in bytes — used for quota pre-check. */
    @Column(name = "total_size_bytes", nullable = false, updatable = false)
    private long totalSizeBytes;

    /** Total number of parts the client intends to upload (≥ 1). */
    @Column(name = "total_parts", nullable = false, updatable = false)
    private int totalParts;

    /**
     * Array of completed part descriptors, e.g. {@code [{"partNumber":1,"etag":"abc..."}]}.
     * Appended on each successful part upload.
     */
    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "uploaded_parts", columnDefinition = "jsonb", nullable = false)
    @Builder.Default
    private List<PartInfo> uploadedParts = List.of();

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private MultipartUploadStatus status = MultipartUploadStatus.IN_PROGRESS;

    /** Session expiry — {@code created_at + 24 hours}. */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    // ─── Nested value object ─────────────────────────────────────────────────────

    /**
     * Immutable descriptor of a completed multipart part.
     *
     * @param partNumber 1-based part number
     * @param etag       ETag returned by MinIO for this part
     */
    public record PartInfo(int partNumber, String etag) {}
}

