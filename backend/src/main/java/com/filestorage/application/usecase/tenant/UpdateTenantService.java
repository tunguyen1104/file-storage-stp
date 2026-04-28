package com.filestorage.application.usecase.tenant;

import com.filestorage.adapter.out.persistence.entity.Tenant;
import com.filestorage.adapter.out.persistence.mapper.TenantViewMapper;
import com.filestorage.adapter.out.persistence.repository.TenantJpaRepository;
import com.filestorage.domain.model.tenant.TenantView;
import com.filestorage.domain.port.in.tenant.UpdateTenantUseCase;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class UpdateTenantService implements UpdateTenantUseCase {

    private final TenantJpaRepository tenantRepository;
    private final TenantViewMapper tenantViewMapper;

    @Override
    @Transactional
    public TenantView execute(UpdateTenantCommand command) {

        Tenant tenant = tenantRepository.findById(command.tenantId())
                .orElseThrow(() -> new AppException(ErrorCode.TENANT_NOT_FOUND));

        tenant.setName(command.name().trim());

        String slug = command.slug().trim().toLowerCase();
        if (tenantRepository.existsBySlugAndIdNot(slug, tenant.getId())) {
            throw new AppException(ErrorCode.SLUG_ALREADY_EXISTS);
        }
        tenant.setSlug(slug);


        if (command.storageQuotaBytes() != null) {
            long used = tenant.getStorageUsedBytes() == null ? 0L : tenant.getStorageUsedBytes();
            if (command.storageQuotaBytes() < used) {
                throw new AppException(ErrorCode.QUOTA_BELOW_USED);
            }
            tenant.setStorageQuotaBytes(command.storageQuotaBytes());
        }

        return tenantViewMapper.toTenantView(tenantRepository.save(tenant));
    }
}

