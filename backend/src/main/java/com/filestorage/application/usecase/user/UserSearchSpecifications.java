package com.filestorage.application.usecase.user;

import com.filestorage.adapter.out.persistence.entity.User;
import com.filestorage.domain.model.enums.RoleCode;
import org.springframework.data.jpa.domain.Specification;

import java.util.UUID;

final class UserSearchSpecifications {

    private UserSearchSpecifications() {
    }

    static Specification<User> byTenant(UUID tenantId) {
        return (root, query, cb) -> tenantId == null ? cb.conjunction() : cb.equal(root.get("tenant").get("id"), tenantId);
    }

    static Specification<User> byIsActive(Boolean isActive) {
        return (root, query, cb) -> isActive == null ? cb.conjunction() : cb.equal(root.get("isActive"), isActive);
    }

    static Specification<User> byRole(RoleCode roleCode) {
        return (root, query, cb) -> roleCode == null ? cb.conjunction() : cb.equal(root.get("role").get("code"), roleCode);
    }

    static Specification<User> bySearch(String search) {
        return (root, query, cb) -> {
            if (search == null || search.isBlank()) {
                return cb.conjunction();
            }
            String pattern = "%" + search.trim().toLowerCase() + "%";
            return cb.or(
                    cb.like(cb.lower(root.get("fullName")), pattern),
                    cb.like(cb.lower(root.get("email")), pattern)
            );
        };
    }
}

