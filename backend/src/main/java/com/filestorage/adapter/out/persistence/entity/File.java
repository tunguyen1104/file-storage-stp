package com.filestorage.adapter.out.persistence.entity;

import com.filestorage.domain.model.enums.FileStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;

@Entity
@Table(
        name = "files",
        indexes = {
                @Index(name = "idx_files_tenant_id", columnList = "tenant_id"),
                @Index(name = "idx_files_folder_id", columnList = "folder_id"),
                @Index(name = "idx_files_tenant_created", columnList = "tenant_id,created_at"),
                @Index(name = "idx_files_uploaded_by", columnList = "uploaded_by"),
                @Index(name = "idx_files_scheduled_delete", columnList = "scheduled_delete_at"),
                @Index(name = "idx_files_pending_upload", columnList = "created_at")
        }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class File extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "folder_id")
    private Folder folder;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "tenant_id", nullable = false)
    private Tenant tenant;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "uploaded_by", nullable = false)
    private User uploadedBy;

    @Column(name = "original_name", length = 500, nullable = false)
    private String originalName;

    @Column(name = "storage_key", length = 1000, nullable = false)
    private String storageKey;

    @Column(name = "mime_type", length = 100)
    private String mimeType;

    @Column(name = "size_bytes", nullable = false)
    private Long sizeBytes;

    @Column(name = "checksum_md5", length = 32)
    private String checksumMd5;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    @Builder.Default
    private FileStatus status = FileStatus.ACTIVE;

    @Column(name = "deleted_at")
    private Instant deletedAt;

    @Column(name = "scheduled_delete_at")
    private Instant scheduledDeleteAt;

    @Column(name = "thumbnail_key", length = 1000)
    private String thumbnailKey;

    @Column(name = "is_indexed", nullable = false)
    @Builder.Default
    private boolean isIndexed = false;
}

