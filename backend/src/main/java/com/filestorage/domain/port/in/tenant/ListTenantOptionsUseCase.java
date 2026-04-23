package com.filestorage.domain.port.in.tenant;

import com.filestorage.domain.model.enums.RoleCode;
import com.filestorage.domain.model.tenant.TenantOptionView;

import java.util.List;
import java.util.UUID;

public interface ListTenantOptionsUseCase {

    List<TenantOptionView> execute(ListTenantOptionsQuery query);

    record ListTenantOptionsQuery(
            UUID actorUserId,
            RoleCode actorRole
    ) {
    }
}

