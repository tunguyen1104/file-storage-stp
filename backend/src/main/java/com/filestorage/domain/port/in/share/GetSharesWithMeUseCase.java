package com.filestorage.domain.port.in.share;

import com.filestorage.domain.model.share.SharedWithMeView;
import org.springframework.data.domain.Page;

public interface GetSharesWithMeUseCase {

    Page<SharedWithMeView> getSharesWithMe(GetSharesWithMeQuery query);
}
