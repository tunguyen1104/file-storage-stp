package com.filestorage.adapter.out.persistence.repository;

import com.filestorage.adapter.out.persistence.entity.File;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface FileJpaRepository extends JpaRepository<File, UUID>, JpaSpecificationExecutor<File> {

	java.util.Optional<File> findByIdAndTenantId(UUID id, UUID tenantId);
}


