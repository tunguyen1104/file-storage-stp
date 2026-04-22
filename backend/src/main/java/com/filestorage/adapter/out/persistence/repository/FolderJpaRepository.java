package com.filestorage.adapter.out.persistence.repository;

import com.filestorage.adapter.out.persistence.entity.Folder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface FolderJpaRepository extends JpaRepository<Folder, UUID>, JpaSpecificationExecutor<Folder> {

	java.util.Optional<Folder> findByIdAndTenantId(UUID id, UUID tenantId);
}


