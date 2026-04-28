package com.filestorage.adapter.out.persistence.repository;

import com.filestorage.adapter.out.persistence.entity.FileShare;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.UUID;

@Repository
public interface FileShareJpaRepository extends JpaRepository<FileShare, UUID> {

    void deleteByFileId(UUID fileId);
}

