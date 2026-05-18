package com.filestorage.domain.port.in.share;

import com.filestorage.domain.model.share.SharePageView;

public interface GetMySharesUseCase {

    SharePageView getMyShares(GetMySharesQuery query);
}
