package com.filestorage.application.usecase.tenant;

import com.filestorage.adapter.out.persistence.mapper.TenantViewMapper;
import com.filestorage.adapter.out.persistence.repository.TenantJpaRepository;
import com.filestorage.adapter.out.persistence.repository.UserJpaRepository;
import com.filestorage.domain.model.tenant.TenantPageView;
import com.filestorage.domain.model.tenant.TenantView;
import com.filestorage.domain.port.in.tenant.ListTenantsUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ListTenantsService implements ListTenantsUseCase {

    private final TenantJpaRepository tenantRepository;
    private final UserJpaRepository userRepository;
    private final TenantViewMapper tenantViewMapper;

    @Override
    @Transactional(readOnly = true)
    public TenantPageView execute(ListTenantsQuery query) {
        TenantAccessPolicy.requireSuperAdmin(query.actorRole());

        int page = Math.max(1, query.page());
        int limit = Math.min(100, Math.max(1, query.limit()));

        var pageable = PageRequest.of(page - 1, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        Specification<com.filestorage.adapter.out.persistence.entity.Tenant> spec = Specification
                .where(TenantSearchSpecifications.byIsActive(query.isActive()))
                .and(TenantSearchSpecifications.bySearch(query.search()));

        var result = tenantRepository.findAll(spec, pageable);
        List<TenantView> tenants = result.getContent().stream().map(tenantViewMapper::toTenantView).toList();
        Map<UUID, Long> userCounts = userCountsByTenantId(tenants);
        List<TenantView> data = tenants.stream()
                .map(tenant -> withUserCount(tenant, userCounts.getOrDefault(tenant.id(), 0L)))
                .toList();

        return new TenantPageView(data, page, limit, result.getTotalElements(), result.getTotalPages());
    }

    private Map<UUID, Long> userCountsByTenantId(List<TenantView> tenants) {
        if (tenants.isEmpty()) {
            return Map.of();
        }

        List<UUID> tenantIds = tenants.stream().map(TenantView::id).toList();
        return userRepository.countUsersByTenantIds(tenantIds).stream()
                .collect(Collectors.toMap(
                        UserJpaRepository.TenantUserCount::getTenantId,
                        UserJpaRepository.TenantUserCount::getUserCount
                ));
    }

    private TenantView withUserCount(TenantView tenant, long userCount) {
        return new TenantView(
                tenant.id(),
                tenant.name(),
                tenant.slug(),
                tenant.storageQuotaBytes(),
                tenant.storageUsedBytes(),
                tenant.isActive(),
                userCount,
                tenant.createdBy(),
                tenant.createdAt(),
                tenant.updatedAt()
        );
    }
}
