package com.filestorage.adapter.out.persistence.entity;

import com.filestorage.domain.model.enums.PermissionFile;
import com.filestorage.domain.model.enums.ShareStatus;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "file_shares")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
public class FileShare {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(updatable = false, nullable = false, columnDefinition = "uuid")
    private UUID id;

    @Column(name = "tenant_id", nullable = false)
    private UUID tenantId;

    @Column(name = "file_id")
    private UUID fileId;

    @Column(name = "folder_id")
    private UUID folderId;

    @Column(name = "shared_with_user_id")
    private UUID sharedWithUserId;

    @Column(name = "shared_with_tenant_id")
    private UUID sharedWithTenantId;

    @Enumerated(EnumType.STRING)
    @JdbcTypeCode(SqlTypes.NAMED_ENUM)
    @Column(name = "permission", columnDefinition = "permission_file")
    private PermissionFile permission;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ShareStatus status = ShareStatus.ACTIVE;

    @Column(name = "shared_by", nullable = false)
    private UUID sharedBy;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @Column(name = "all_tenants", nullable = false)
    private boolean allTenants = false;
}
