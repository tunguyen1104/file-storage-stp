package com.filestorage.domain.port.in.share;

import com.filestorage.domain.model.share.SharedWithMePageView;

public interface GetSharesWithMeUseCase {

    SharedWithMePageView getSharesWithMe(GetSharesWithMeQuery query);
}
