package com.filestorage.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

@Entity
@Table(name = "tenants")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Tenant extends BaseEntity {

    @Column(name = "name", length = 255, nullable = false)
    private String name;

    @Column(name = "slug", length = 100, unique = true, nullable = false)
    private String slug; // CHECK: ^[a-z0-9-]+$

    @Column(name = "storage_quota_bytes", nullable = false)
    @Builder.Default
    private Long storageQuotaBytes = 53_687_091_200L; // 50 GB

    @Column(name = "storage_used_bytes", nullable = false)
    @Builder.Default
    private Long storageUsedBytes = 0L;

    private boolean isActive;

    @Column(name = "created_by", columnDefinition = "uuid")
    private UUID createdBy;
}

