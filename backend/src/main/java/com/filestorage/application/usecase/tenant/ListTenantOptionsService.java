package com.filestorage.application.usecase.tenant;

import com.filestorage.adapter.out.persistence.entity.Tenant;
import com.filestorage.adapter.out.persistence.repository.TenantJpaRepository;
import com.filestorage.domain.model.tenant.TenantOptionView;
import com.filestorage.domain.port.in.tenant.ListTenantOptionsUseCase;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
public class ListTenantOptionsService implements ListTenantOptionsUseCase {

    private final TenantJpaRepository tenantRepository;

    @Override
    @Transactional(readOnly = true)
    public List<TenantOptionView> execute(ListTenantOptionsQuery query) {
        return tenantRepository.findAll(Sort.by(Sort.Direction.ASC, "name"))
                .stream()
                .filter(Tenant::isActive)
                .map(t -> new TenantOptionView(t.getId(), t.getName()))
                .toList();
    }
}


