package com.filestorage.application.usecase.tenant;

import com.filestorage.adapter.out.persistence.entity.Tenant;
import com.filestorage.adapter.out.persistence.mapper.TenantViewMapper;
import com.filestorage.adapter.out.persistence.repository.TenantJpaRepository;
import com.filestorage.domain.model.tenant.TenantView;
import com.filestorage.domain.port.in.tenant.CreateTenantUseCase;
import com.filestorage.shared.exception.AppException;
import com.filestorage.shared.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CreateTenantService implements CreateTenantUseCase {

    private static final long DEFAULT_TENANT_QUOTA_BYTES = 53_687_091_200L;

    private final TenantJpaRepository tenantRepository;
    private final TenantViewMapper tenantViewMapper;

    @Override
    @Transactional
    public TenantView execute(CreateTenantCommand command) {

        String slug = normalizeSlug(command.slug());
        if (tenantRepository.existsBySlug(slug)) {
            throw new AppException(ErrorCode.SLUG_ALREADY_EXISTS);
        }

        Tenant created = Tenant.builder()
                .name(normalizeName(command.name()))
                .slug(slug)
                .storageQuotaBytes(resolveStorageQuota(command.storageQuotaBytes()))
                .storageUsedBytes(0L)
                .isActive(true)
                .createdBy(command.actorUserId())
                .build();

        return tenantViewMapper.toTenantView(tenantRepository.save(created));
    }

    private long resolveStorageQuota(Long requestedQuota) {
        return requestedQuota == null ? DEFAULT_TENANT_QUOTA_BYTES : requestedQuota;
    }

    private String normalizeName(String value) {
        if (value == null || value.isBlank()) {
            throw new AppException(ErrorCode.INVALID_PAYLOAD);
        }
        return value.trim();
    }

    private String normalizeSlug(String value) {
        if (value == null || value.isBlank()) {
            throw new AppException(ErrorCode.INVALID_PAYLOAD);
        }
        return value.trim().toLowerCase();
    }
}
