package com.filestorage.adapter.out.persistence.repository;

import com.filestorage.adapter.out.persistence.entity.FileRagDocument;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface FileRagDocumentJpaRepository extends JpaRepository<FileRagDocument, UUID> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT d FROM FileRagDocument d WHERE d.fileId = :fileId")
    Optional<FileRagDocument> findByFileIdForUpdate(@Param("fileId") UUID fileId);
}
