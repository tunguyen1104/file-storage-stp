package com.filestorage.adapter.out.persistence.entity;

import com.filestorage.domain.model.enums.PermissionFile;
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
    @Column(name = "permission", nullable = false, columnDefinition = "permission_file")
    private PermissionFile permission;

    @Column(name = "shared_by", nullable = false)
    private UUID sharedBy;

    @Column(name = "expires_at")
    private Instant expiresAt;

    @Column(name = "created_at", insertable = false, updatable = false)
    private Instant createdAt;
}
