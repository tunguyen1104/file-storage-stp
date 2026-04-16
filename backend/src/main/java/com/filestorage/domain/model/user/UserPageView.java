package com.filestorage.domain.model.user;

import java.util.List;

public record UserPageView(
        List<UserView> data,
        int page,
        int limit,
        long total,
        int totalPages
) {
}

