package com.filestorage.application.usecase.audit;

import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class AuditAccessPolicy {

    public EffectiveQuery applyTo(RoleCode role, UUID actorTenantId, UUID requestedTenantId) {
        if (role == null) {
            throw new AppException(ErrorCode.ACCESS_DENIED);
        }

        return switch (role) {
            case SUPER_ADMIN -> new EffectiveQuery(requestedTenantId, null);
            case TENANT_ADMIN -> new EffectiveQuery(actorTenantId, "SUPER_ADMIN");
            case MEMBER -> throw new AppException(ErrorCode.FORBIDDEN);
        };
    }

    public record EffectiveQuery(UUID tenantId, String excludeActorRole) {
    }
}
