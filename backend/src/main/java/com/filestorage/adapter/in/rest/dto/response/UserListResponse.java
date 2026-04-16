package com.filestorage.adapter.in.rest.dto.response;

import java.util.List;

public record UserListResponse(
        List<UserListItemResponse> data,
        UserPaginationResponse pagination
) {
}

