package com.filestorage.adapter.in.rest.dto.response;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class PageResponse<T> {

    private List<T> data;
    private int pageSize;
    private int totalPages;
    private long totalElements;
    private int currentPage;
}

