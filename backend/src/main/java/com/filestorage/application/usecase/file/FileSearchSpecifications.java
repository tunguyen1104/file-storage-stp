package com.filestorage.application.usecase.file;

import com.filestorage.adapter.out.persistence.entity.File;
import com.filestorage.domain.model.enums.FileStatus;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

final class FileSearchSpecifications {

    private FileSearchSpecifications() {
    }

    static Specification<File> byTenant(UUID tenantId) {
        return (root, query, cb) -> tenantId == null ? cb.conjunction() : cb.equal(root.get("tenant").get("id"), tenantId);
    }

    static Specification<File> byFolderId(UUID folderId) {
        return (root, query, cb) -> folderId == null ? cb.conjunction() : cb.equal(root.get("folder").get("id"), folderId);
    }

    static Specification<File> byStatus(FileStatus status) {
        return (root, query, cb) -> status == null ? cb.conjunction() : cb.equal(root.get("status"), status);
    }

    static Specification<File> byUploadedBy(UUID uploadedBy) {
        return (root, query, cb) -> uploadedBy == null ? cb.conjunction() : cb.equal(root.get("uploadedBy").get("id"), uploadedBy);
    }


    static Specification<File> bySearch(String search) {
        return (root, query, cb) -> {
            if (search == null || search.isBlank()) {
                return cb.conjunction();
            }
            String pattern = "%" + search.trim().toLowerCase() + "%";
            return cb.like(cb.lower(root.get("originalName")), pattern);
        };
    }
}
