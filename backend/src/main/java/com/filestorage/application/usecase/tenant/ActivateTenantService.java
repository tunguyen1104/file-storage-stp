package com.filestorage.application.usecase.tenant;

import com.filestorage.adapter.out.persistence.entity.Tenant;
import com.filestorage.adapter.out.persistence.mapper.TenantViewMapper;
import com.filestorage.adapter.out.persistence.repository.TenantJpaRepository;
import com.filestorage.domain.model.tenant.TenantView;
import com.filestorage.domain.port.in.tenant.ActivateTenantUseCase;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ActivateTenantService implements ActivateTenantUseCase {

    private final TenantJpaRepository tenantRepository;
    private final TenantViewMapper tenantViewMapper;

    @Override
    @Transactional
    public TenantView execute(ActivateTenantCommand command) {
        TenantAccessPolicy.requireSuperAdmin(command.actorRole());

        Tenant tenant = tenantRepository.findByIdForUpdate(command.tenantId())
                .orElseThrow(() -> new AppException(ErrorCode.TENANT_NOT_FOUND));

        if (!tenant.isActive()) {
            tenant.setActive(true);
            tenantRepository.save(tenant);
        }

        return tenantViewMapper.toTenantView(tenant);
    }
}
