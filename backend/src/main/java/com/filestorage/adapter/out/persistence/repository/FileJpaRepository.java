package com.filestorage.adapter.out.persistence.repository;

import com.filestorage.adapter.out.persistence.entity.File;
import com.filestorage.domain.model.enums.FileStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FileJpaRepository extends JpaRepository<File, UUID>, JpaSpecificationExecutor<File> {

	java.util.Optional<File> findByIdAndTenantId(UUID id, UUID tenantId);

	boolean existsByFolderIdAndStatus(UUID folderId, FileStatus status);

	/**
	 * Finds stale PENDING files for cleanup: those with the given status created before {@code cutoff}.
	 * Used by {@code PendingFileCleanupJob}.
	 */
	List<File> findByStatusAndCreatedAtBefore(FileStatus status, Instant cutoff);

	/**
	 * Sums sizeBytes of all PENDING and SCANNING files for a user.
	 * Used to account for in-flight uploads when checking quota.
	 */
	@org.springframework.data.jpa.repository.Query(
	        "SELECT COALESCE(SUM(f.sizeBytes), 0) FROM File f " +
	        "WHERE f.uploadedBy.id = :userId AND f.status IN :statuses")
	long sumSizeBytesByUserAndStatuses(
	        @Param("userId") UUID userId,
	        @Param("statuses") List<FileStatus> statuses);

	/**
	 * Loads a file by id with uploadedBy eagerly fetched via JOIN FETCH,
	 * avoiding a secondary lazy-load query when the uploader is accessed.
	 */
	@Query("SELECT f FROM File f JOIN FETCH f.uploadedBy WHERE f.id = :id")
    Optional<File> findByIdWithUploader(@Param("id") UUID id);
}
