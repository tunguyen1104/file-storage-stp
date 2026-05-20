package com.filestorage.domain.port.in.search;

import com.filestorage.domain.model.search.SuggestView;

public interface SuggestUseCase {

    SuggestView execute(SuggestQuery query);
}
