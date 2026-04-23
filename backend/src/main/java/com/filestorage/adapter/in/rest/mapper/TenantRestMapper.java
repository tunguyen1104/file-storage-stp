package com.filestorage.adapter.in.rest.mapper;

import com.filestorage.adapter.in.rest.dto.response.PageResponse;
import com.filestorage.adapter.in.rest.dto.response.TenantOptionResponse;
import com.filestorage.adapter.in.rest.dto.response.TenantResponse;
import com.filestorage.domain.model.tenant.TenantOptionView;
import com.filestorage.domain.model.tenant.TenantPageView;
import com.filestorage.domain.model.tenant.TenantView;
import com.filestorage.infrastructure.config.GlobalMapperConfig;
import org.mapstruct.Mapper;

@Mapper(config = GlobalMapperConfig.class)
public interface TenantRestMapper {

    TenantResponse toTenantResponse(TenantView view);

    TenantOptionResponse toTenantOptionResponse(TenantOptionView view);

    default PageResponse<TenantResponse> toPageResponse(TenantPageView pageView) {
        return PageResponse.<TenantResponse>builder()
                .data(pageView.data().stream().map(this::toTenantResponse).toList())
                .currentPage(pageView.page())
                .pageSize(pageView.limit())
                .totalElements(pageView.total())
                .totalPages(pageView.totalPages())
                .build();
    }
}

