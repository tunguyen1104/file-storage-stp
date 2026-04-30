package com.filestorage.adapter.out.persistence.repository;

import com.filestorage.adapter.out.persistence.entity.Folder;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface FolderJpaRepository extends JpaRepository<Folder, UUID>, JpaSpecificationExecutor<Folder> {

	java.util.Optional<Folder> findByIdAndTenantId(UUID id, UUID tenantId);

	boolean existsByTenantIdAndParentIdAndName(UUID tenantId, UUID parentId, String name);

	boolean existsByTenantIdAndParentId(UUID tenantId, UUID parentId);

	@org.springframework.data.jpa.repository.Query(value = """
			WITH RECURSIVE tree AS (
				SELECT id, name, parent_id, 0 AS depth
				FROM folders
				WHERE id = :folderId AND tenant_id = :tenantId
				UNION ALL
				SELECT f.id, f.name, f.parent_id, t.depth + 1
				FROM folders f
				JOIN tree t ON t.parent_id = f.id
			)
			SELECT id, name, depth
			FROM tree
			ORDER BY depth DESC
			""", nativeQuery = true)
	java.util.List<FolderBreadcrumbProjection> findBreadcrumbs(UUID folderId, UUID tenantId);

	@org.springframework.data.jpa.repository.Query(value = """
			WITH RECURSIVE tree AS (
				SELECT id
				FROM folders
				WHERE id = :folderId AND tenant_id = :tenantId
				UNION ALL
				SELECT f.id
				FROM folders f
				JOIN tree t ON f.parent_id = t.id
			)
			SELECT COALESCE(SUM(fl.size_bytes), 0)
			FROM files fl
			WHERE fl.folder_id IN (SELECT id FROM tree)
			  AND fl.status = 'ACTIVE'
			""", nativeQuery = true)
	Long sumActiveFileSize(UUID folderId, UUID tenantId);

	@org.springframework.data.jpa.repository.Query(value = """
			WITH RECURSIVE tree AS (
				SELECT id
				FROM folders
				WHERE id = :folderId AND tenant_id = :tenantId
				UNION ALL
				SELECT f.id
				FROM folders f
				JOIN tree t ON f.parent_id = t.id
			)
			SELECT EXISTS (SELECT 1 FROM tree WHERE id = :candidateParentId)
			""", nativeQuery = true)
	boolean isDescendant(UUID folderId, UUID tenantId, UUID candidateParentId);

	interface FolderBreadcrumbProjection {
		java.util.UUID getId();
		String getName();
		int getDepth();
	}
}
