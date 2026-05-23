package com.filestorage.application.usecase.audit;

import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class AuditAccessPolicyTest {

    private AuditAccessPolicy policy;

    @BeforeEach
    void setUp() {
        policy = new AuditAccessPolicy();
    }

    @Test
    void superAdminPassesQueryUnchanged() {
        UUID actorTenant = UUID.randomUUID();
        UUID queriedTenant = UUID.randomUUID();

        var effective = policy.applyTo(RoleCode.SUPER_ADMIN, actorTenant, queriedTenant);

        assertEquals(queriedTenant, effective.tenantId());
        assertNull(effective.excludeActorRole());
    }

    @Test
    void tenantAdminOverridesTenantIdAndExcludesSuperAdmin() {
        UUID actorTenant = UUID.randomUUID();
        UUID otherTenant = UUID.randomUUID();

        var effective = policy.applyTo(RoleCode.TENANT_ADMIN, actorTenant, otherTenant);

        assertEquals(actorTenant, effective.tenantId(), "tenantId must be hard-overridden to actor tenant");
        assertEquals("SUPER_ADMIN", effective.excludeActorRole());
    }

    @Test
    void memberIsForbidden() {
        AppException ex = assertThrows(AppException.class,
                () -> policy.applyTo(RoleCode.MEMBER, UUID.randomUUID(), null));
        assertEquals(ErrorCode.FORBIDDEN, ex.getError());
    }

    @Test
    void nullRoleIsAccessDenied() {
        AppException ex = assertThrows(AppException.class,
                () -> policy.applyTo(null, UUID.randomUUID(), null));
        assertEquals(ErrorCode.ACCESS_DENIED, ex.getError());
    }
}
