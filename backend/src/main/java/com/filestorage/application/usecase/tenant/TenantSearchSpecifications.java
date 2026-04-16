package com.filestorage.application.usecase.tenant;

import com.filestorage.adapter.out.persistence.entity.Tenant;
import org.springframework.data.jpa.domain.Specification;

final class TenantSearchSpecifications {

    private TenantSearchSpecifications() {
    }

    static Specification<Tenant> byIsActive(Boolean isActive) {
        return (root, query, cb) -> isActive == null ? cb.conjunction() : cb.equal(root.get("isActive"), isActive);
    }

    static Specification<Tenant> bySearch(String search) {
        return (root, query, cb) -> {
            if (search == null || search.isBlank()) {
                return cb.conjunction();
            }
            String pattern = "%" + search.trim().toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("name")), pattern),
                    cb.like(cb.lower(root.get("slug")), pattern)
            );
        };
    }
}

