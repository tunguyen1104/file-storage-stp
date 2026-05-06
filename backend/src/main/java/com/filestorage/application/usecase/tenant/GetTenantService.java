package com.filestorage.application.usecase.tenant;

import com.filestorage.adapter.out.persistence.mapper.TenantViewMapper;
import com.filestorage.adapter.out.persistence.repository.TenantJpaRepository;
import com.filestorage.adapter.out.persistence.repository.UserJpaRepository;
import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.tenant.TenantView;
import com.filestorage.domain.port.in.tenant.GetTenantUseCase;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class GetTenantService implements GetTenantUseCase {

    private final TenantJpaRepository tenantRepository;
    private final UserJpaRepository userRepository;
    private final TenantViewMapper tenantViewMapper;

    @Override
    @Transactional(readOnly = true)
    public TenantView execute(GetTenantQuery query) {
        ensureCanReadTenant(query);

        var tenant = tenantRepository.findById(query.tenantId())
                .orElseThrow(() -> new AppException(ErrorCode.TENANT_NOT_FOUND));
        return withUserCount(tenantViewMapper.toTenantView(tenant));
    }

    private void ensureCanReadTenant(GetTenantQuery query) {
        if (query.actorRole() == RoleCode.SUPER_ADMIN) {
            return;
        }

        if (query.actorRole() == RoleCode.TENANT_ADMIN
                && query.actorTenantId() != null
                && query.actorTenantId().equals(query.tenantId())) {
            return;
        }

        throw new AppException(ErrorCode.FORBIDDEN);
    }

    private TenantView withUserCount(TenantView tenant) {
        Map<UUID, Long> userCounts = userCountsByTenantId(List.of(tenant.id()));
        return new TenantView(
                tenant.id(),
                tenant.name(),
                tenant.slug(),
                tenant.storageQuotaBytes(),
                tenant.storageUsedBytes(),
                tenant.isActive(),
                userCounts.getOrDefault(tenant.id(), 0L),
                tenant.createdBy(),
                tenant.createdAt(),
                tenant.updatedAt()
        );
    }

    private Map<UUID, Long> userCountsByTenantId(Collection<UUID> tenantIds) {
        return userRepository.countUsersByTenantIds(tenantIds).stream()
                .collect(Collectors.toMap(
                        UserJpaRepository.TenantUserCount::getTenantId,
                        UserJpaRepository.TenantUserCount::getUserCount
                ));
    }
}
