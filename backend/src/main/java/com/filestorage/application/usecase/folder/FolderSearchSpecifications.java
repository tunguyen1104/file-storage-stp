package com.filestorage.application.usecase.folder;

import com.filestorage.adapter.out.persistence.entity.Folder;
import com.filestorage.shared.util.VietnameseUtils;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

final class FolderSearchSpecifications {

    private FolderSearchSpecifications() {
    }

    static Specification<Folder> byTenant(UUID tenantId) {
        return (root, query, cb) -> tenantId == null ? cb.conjunction() : cb.equal(root.get("tenant").get("id"), tenantId);
    }

    static Specification<Folder> byParentId(UUID parentId) {
        return (root, query, cb) -> parentId == null
                ? cb.isNull(root.get("parent"))
                : cb.equal(root.get("parent").get("id"), parentId);
    }

    static Specification<Folder> byCreatedBy(UUID createdBy) {
        return (root, query, cb) -> createdBy == null ? cb.conjunction() : cb.equal(root.get("createdBy").get("id"), createdBy);
    }

    static Specification<Folder> bySearch(String search) {
        return (root, query, cb) -> {
            if (search == null || search.isBlank()) {
                return cb.conjunction();
            }
            String trimmed = search.trim();
            if (VietnameseUtils.containsAccent(trimmed)) {
                String pattern = "%" + trimmed.toLowerCase() + "%";
                return cb.like(cb.lower(root.get("name")), pattern);
            }

            String normalized = VietnameseUtils.removeAccent(trimmed).toLowerCase();
            String pattern = "%" + normalized + "%";
            return cb.like(cb.function("vi_unaccent", String.class, cb.lower(root.get("name"))), pattern);
        };
    }
}

