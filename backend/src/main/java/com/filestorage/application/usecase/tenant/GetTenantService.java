package com.filestorage.application.usecase.tenant;

import com.filestorage.adapter.out.persistence.mapper.TenantViewMapper;
import com.filestorage.adapter.out.persistence.repository.TenantJpaRepository;
import com.filestorage.domain.model.tenant.TenantView;
import com.filestorage.domain.port.in.tenant.GetTenantUseCase;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GetTenantService implements GetTenantUseCase {

    private final TenantJpaRepository tenantRepository;
    private final TenantViewMapper tenantViewMapper;

    @Override
    @Transactional(readOnly = true)
    public TenantView execute(GetTenantQuery query) {
        TenantAccessPolicy.requireSuperAdmin(query.actorRole());

        var tenant = tenantRepository.findById(query.tenantId())
                .orElseThrow(() -> new AppException(ErrorCode.TENANT_NOT_FOUND));
        return tenantViewMapper.toTenantView(tenant);
    }
}

