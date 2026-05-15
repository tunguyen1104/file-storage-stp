package com.filestorage.adapter.in.rest.dto.request;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

public record RenewShareExpirationRequest(
        @NotNull @Positive Long expireTime
) {
}
