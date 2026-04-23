package com.filestorage.application.usecase.tenant;

import com.filestorage.adapter.out.persistence.entity.Tenant;
import com.filestorage.adapter.out.persistence.mapper.TenantViewMapper;
import com.filestorage.adapter.out.persistence.repository.TenantJpaRepository;
import com.filestorage.domain.model.tenant.TenantView;
import com.filestorage.domain.port.in.tenant.DeactivateTenantUseCase;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class DeactivateTenantService implements DeactivateTenantUseCase {

    private static final String SYSTEM_TENANT_SLUG = "system";

    private final TenantJpaRepository tenantRepository;
    private final TenantViewMapper tenantViewMapper;

    @Override
    @Transactional
    public TenantView execute(DeactivateTenantCommand command) {

        Tenant tenant = tenantRepository.findById(command.tenantId())
                .orElseThrow(() -> new AppException(ErrorCode.TENANT_NOT_FOUND));

        if (SYSTEM_TENANT_SLUG.equals(tenant.getSlug())) {
            throw new AppException(ErrorCode.valueOf("SYSTEM_TENANT_CANNOT_BE_DELETED"));
        }

        if (tenant.isActive()) {
            tenant.setActive(false);
            tenantRepository.save(tenant);
        }

        return tenantViewMapper.toTenantView(tenant);
    }
}
