package com.filestorage.application.usecase.tenant;

import com.filestorage.adapter.out.persistence.mapper.TenantViewMapper;
import com.filestorage.adapter.out.persistence.repository.TenantJpaRepository;
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

@Service
@RequiredArgsConstructor
public class ListTenantsService implements ListTenantsUseCase {

    private final TenantJpaRepository tenantRepository;
    private final TenantViewMapper tenantViewMapper;

    @Override
    @Transactional(readOnly = true)
    public TenantPageView execute(ListTenantsQuery query) {

        int page = Math.max(1, query.page());
        int limit = Math.min(100, Math.max(1, query.limit()));

        var pageable = PageRequest.of(page - 1, limit, Sort.by(Sort.Direction.DESC, "createdAt"));
        Specification<com.filestorage.adapter.out.persistence.entity.Tenant> spec = Specification
                .where(TenantSearchSpecifications.byIsActive(query.isActive()))
                .and(TenantSearchSpecifications.bySearch(query.search()));

        var result = tenantRepository.findAll(spec, pageable);
        List<TenantView> data = result.getContent().stream().map(tenantViewMapper::toTenantView).toList();

        return new TenantPageView(data, page, limit, result.getTotalElements(), result.getTotalPages());
    }
}

