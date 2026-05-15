package com.filestorage.domain.port.in.share;

import com.filestorage.domain.model.share.ShareView;

public interface ShareFileWithTenantUseCase {

    ShareView shareFileWithTenant(ShareWithTenantCommand command);
}
