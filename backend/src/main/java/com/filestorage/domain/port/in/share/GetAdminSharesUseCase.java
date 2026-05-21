package com.filestorage.domain.port.in.share;

import com.filestorage.domain.model.share.ShareListPageView;

public interface GetAdminSharesUseCase {

    ShareListPageView getAdminShares(GetAdminSharesQuery query);
}
