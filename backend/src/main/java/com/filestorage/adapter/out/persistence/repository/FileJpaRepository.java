package com.filestorage.adapter.out.persistence.repository;

import com.filestorage.adapter.out.persistence.entity.File;
import com.filestorage.domain.model.enums.FileStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FileJpaRepository extends JpaRepository<File, UUID>, JpaSpecificationExecutor<File> {

	java.util.Optional<File> findByIdAndTenantId(UUID id, UUID tenantId);

	@Query("SELECT COUNT(f) > 0 FROM File f WHERE f.folder.id = :folderId AND f.status NOT IN :statuses")
	boolean existsByFolderIdAndStatusNotIn(
	        @Param("folderId") UUID folderId,
	        @Param("statuses") List<FileStatus> statuses);

	@Modifying(flushAutomatically = true)
	@Query("UPDATE File f SET f.folder = null WHERE f.folder.id = :folderId AND f.status IN :statuses")
	int clearFolderByFolderIdAndStatusIn(
	        @Param("folderId") UUID folderId,
	        @Param("statuses") List<FileStatus> statuses);

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
	@Query("SELECT f FROM File f JOIN FETCH f.uploadedBy JOIN FETCH f.tenant WHERE f.id = :id")
    Optional<File> findByIdWithUploaderAndTenant(@Param("id") UUID id);

	List<File> findByStatusAndScheduledDeleteAtBefore(FileStatus status, Instant cutoff);

	@Query("SELECT f FROM File f JOIN FETCH f.uploadedBy JOIN FETCH f.tenant " +
	       "WHERE f.isIndexed = false AND f.indexRetryCount < :maxRetries AND f.createdAt < :cutoff")
	List<File> findUnindexedWithUploaderAndTenant(
	        @Param("maxRetries") int maxRetries,
	        @Param("cutoff") Instant cutoff);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("UPDATE File f SET f.originalName = :name WHERE f.id = :id")
	void updateOriginalName(@Param("id") UUID id, @Param("name") String name);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("UPDATE File f SET f.indexRetryCount = f.indexRetryCount + 1 WHERE f.id = :id")
	void incrementIndexRetryCount(@Param("id") UUID id);

	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("UPDATE File f SET f.isIndexed = true, f.contentKey = :contentKey WHERE f.id = :id")
	void markIndexed(@Param("id") UUID id, @Param("contentKey") String contentKey);
}
