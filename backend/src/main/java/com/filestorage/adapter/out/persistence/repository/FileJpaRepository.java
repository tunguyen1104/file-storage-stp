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

	List<File> findByStatusAndScheduledDeleteAtBefore(FileStatus status, Instant cutoff);

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

	/**
	 * Finds files in the given status whose scheduled deletion time has passed
	 * ({@code scheduledDeleteAt <= cutoff}). Used by {@code SoftDeletedFileCleanupJob}
	 * to locate PENDING_DELETE files that are due for physical purge.
	 */
	List<File> findByStatusAndScheduledDeleteAtBefore(FileStatus status, Instant cutoff);

	/**
	 * Re-fetches a file by ID with a PESSIMISTIC_WRITE lock, but only if its current
	 * status matches the expected value. Used by {@code SoftDeletedFileCleanupJob} to
	 * guard against a concurrent RestoreFileService call changing the status between
	 * the initial bulk query and the actual purge.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("SELECT f FROM File f WHERE f.id = :id AND f.status = :status")
	Optional<File> findByIdAndStatusForUpdate(@Param("id") UUID id, @Param("status") FileStatus status);
}
