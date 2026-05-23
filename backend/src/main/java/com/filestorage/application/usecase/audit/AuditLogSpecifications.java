package com.filestorage.application.usecase.audit;

import com.filestorage.adapter.out.persistence.entity.AuditLog;
import com.filestorage.adapter.out.persistence.entity.User;
import com.filestorage.domain.model.audit.AuditAction;
import jakarta.persistence.criteria.Subquery;
import org.springframework.data.jpa.domain.Specification;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

final class AuditLogSpecifications {

    private AuditLogSpecifications() {
    }

    /**
     * Compose the full audit-log query Specification from the shared filter set.
     * Used by list / stats / export use cases so they apply the exact same filter semantics.
     */
    static Specification<AuditLog> buildFilter(
            UUID effectiveTenantId,
            String userEmail,
            UUID fileId,
            List<AuditAction> actions,
            Instant from,
            Instant to,
            String ipAddress,
            String userAgent,
            String excludeActorRole
    ) {
        return Specification
                .where(byTenantId(effectiveTenantId))
                .and(byUserEmailPrefix(userEmail))
                .and(byFileId(fileId))
                .and(byActionIn(actions))
                .and(byCreatedAtBetween(from, to))
                .and(byIpAddress(ipAddress))
                .and(byUserAgentLike(userAgent))
                .and(excludeActorRole(excludeActorRole));
    }

    static Specification<AuditLog> byTenantId(UUID tenantId) {
        return (root, query, cb) -> tenantId == null ? cb.conjunction() : cb.equal(root.get("tenantId"), tenantId);
    }

    /**
     * Prefix-match on lowercased email. Uses a subquery against the users table so the
     * audit_logs query stays scoped to its own indexes; users.email is unique-indexed.
     * Blank / null filter is treated as "no constraint".
     */
    static Specification<AuditLog> byUserEmailPrefix(String emailPrefix) {
        return (root, query, cb) -> {
            if (emailPrefix == null || emailPrefix.isBlank()) {
                return cb.conjunction();
            }
            String normalized = emailPrefix.trim().toLowerCase();
            Subquery<UUID> sub = query.subquery(UUID.class);
            var userRoot = sub.from(User.class);
            sub.select(userRoot.get("id"));
            sub.where(cb.like(cb.lower(userRoot.get("email")), normalized + "%"));
            return root.get("userId").in(sub);
        };
    }

    static Specification<AuditLog> byFileId(UUID fileId) {
        return (root, query, cb) -> fileId == null ? cb.conjunction() : cb.equal(root.get("fileId"), fileId);
    }

    static Specification<AuditLog> byActionIn(List<AuditAction> actions) {
        return (root, query, cb) -> {
            if (actions == null || actions.isEmpty()) {
                return cb.conjunction();
            }
            return root.get("action").in(actions);
        };
    }

    static Specification<AuditLog> byCreatedAtBetween(Instant from, Instant to) {
        return (root, query, cb) -> {
            if (from != null && to != null) {
                return cb.between(root.get("createdAt"), from, to);
            }
            if (from != null) {
                return cb.greaterThanOrEqualTo(root.get("createdAt"), from);
            }
            if (to != null) {
                return cb.lessThan(root.get("createdAt"), to);
            }
            return cb.conjunction();
        };
    }

    static Specification<AuditLog> byIpAddress(String ipAddress) {
        return (root, query, cb) -> {
            if (ipAddress == null || ipAddress.isBlank()) {
                return cb.conjunction();
            }
            // inet column compared as text via host() cast
            return cb.equal(cb.function("host", String.class, root.get("ipAddress")), ipAddress);
        };
    }

    /**
     * Substring match (case-insensitive) on the user_agent column.
     * No index on user_agent — relies on the tenant/date predicates to cut the working set first.
     */
    static Specification<AuditLog> byUserAgentLike(String userAgent) {
        return (root, query, cb) -> {
            if (userAgent == null || userAgent.isBlank()) {
                return cb.conjunction();
            }
            String pattern = "%" + userAgent.trim().toLowerCase() + "%";
            return cb.like(cb.lower(root.get("userAgent")), pattern);
        };
    }

    /**
     * Excludes rows where extra_data->>'actor_role' equals the given role.
     * Used by tenant_admin to filter out super_admin events.
     * NULL extra_data rows are kept (no actor_role recorded = system event).
     */
    static Specification<AuditLog> excludeActorRole(String excludedRole) {
        return (root, query, cb) -> {
            if (excludedRole == null) {
                return cb.conjunction();
            }
            var extracted = cb.function(
                    "jsonb_extract_path_text",
                    String.class,
                    root.get("extraData"),
                    cb.literal("actor_role")
            );
            return cb.or(extracted.isNull(), cb.notEqual(extracted, excludedRole));
        };
    }
}
