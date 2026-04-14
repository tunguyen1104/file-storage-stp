package com.filestorage.adapter.in.rest.dto.response;

import lombok.Builder;

@Builder
public record TokenResponse(String accessToken, String refreshToken) {
}

