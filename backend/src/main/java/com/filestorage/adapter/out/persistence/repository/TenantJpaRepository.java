package com.filestorage.adapter.out.persistence.repository;

import com.filestorage.adapter.out.persistence.entity.Tenant;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface TenantJpaRepository extends JpaRepository<Tenant, UUID>, JpaSpecificationExecutor<Tenant> {

    Optional<Tenant> findBySlug(String slug);

    boolean existsBySlug(String slug);

    boolean existsBySlugAndIdNot(String slug, UUID id);
}
